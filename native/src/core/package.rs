use crate::consts::{APP_PACKAGE_NAME, MAGISK_VER_CODE};
use crate::daemon::{AID_APP_END, AID_APP_START, AID_USER_OFFSET, MagiskD, to_app_id};
use crate::ffi::{DbEntryKey, get_magisk_tmp, install_apk, uninstall_pkg};
use base::WalkResult::{Abort, Continue, Skip};
use base::{
    BufReadExt, Directory, FsPathBuilder, LoggedResult, ReadExt, ResultExt, Utf8CStrBuf,
    Utf8CString, cstr, error, fd_get_attr, warn,
};
use bit_set::BitSet;
use nix::fcntl::OFlag;
use std::collections::BTreeMap;
use std::fs::File;
use std::io;
use std::io::{Cursor, Read, Seek, SeekFrom};
use std::os::fd::AsRawFd;
use std::time::Duration;

const EOCD_MAGIC: u32 = 0x06054B50;
const APK_SIGNING_BLOCK_MAGIC: [u8; 16] = *b"APK Sig Block 42";
const SIGNATURE_SCHEME_V2_MAGIC: u32 = 0x7109871A;
const PACKAGES_XML: &str = "/data/system/packages.xml";

macro_rules! bad_apk {
    ($msg:literal) => {
        io::Error::new(io::ErrorKind::InvalidData, concat!("cert: ", $msg))
    };
}

/*
 * A v2/v3 signed APK has the format as following
 *
 * +---------------+
 * | zip content   |
 * +---------------+
 * | signing block |
 * +---------------+
 * | central dir   |
 * +---------------+
 * | EOCD          |
 * +---------------+
 *
 * Scan from end of file to find EOCD, and figure our way back to the
 * offset of the signing block. Next, directly extract the certificate
 * from the v2 signature block.
 *
 * All structures above are mostly just for documentation purpose.
 *
 * This method extracts the first certificate of the first signer
 * within the APK v2 signature block.
 */
fn read_certificate(apk: &mut File, version: i32) -> Vec<u8> {
    let res = || -> io::Result<Vec<u8>> {
        let mut u32_val = 0u32;
        let mut u64_val = 0u64;

        // Find EOCD
        for i in 0u16.. {
            let mut comment_sz = 0u16;
            apk.seek(SeekFrom::End(-(size_of_val(&comment_sz) as i64) - i as i64))?;
            apk.read_pod(&mut comment_sz)?;

            if comment_sz == i {
                apk.seek(SeekFrom::Current(-22))?;
                let mut magic = 0u32;
                apk.read_pod(&mut magic)?;
                if magic == EOCD_MAGIC {
                    break;
                }
            }
            if i == 0xffff {
                Err(bad_apk!("invalid APK format"))?;
            }
        }

        // We are now at EOCD + sizeof(magic)
        // Seek and read central_dir_off to find the start of the central directory
        let mut central_dir_off = 0u32;
        apk.seek(SeekFrom::Current(12))?;
        apk.read_pod(&mut central_dir_off)?;

        // Code for parse APK comment to get version code
        if version >= 0 {
            let mut comment_sz = 0u16;
            apk.read_pod(&mut comment_sz)?;
            let mut comment = vec![0u8; comment_sz as usize];
            apk.read_exact(&mut comment)?;
            let mut comment = Cursor::new(&comment);
            let mut apk_ver = 0;
            comment.for_each_prop(|k, v| {
                if k == "versionCode" {
                    apk_ver = v.parse::<i32>().unwrap_or(0);
                    false
                } else {
                    true
                }
            });
        }

        // Next, find the start of the APK signing block
        apk.seek(SeekFrom::Start((central_dir_off - 24) as u64))?;
        apk.read_pod(&mut u64_val)?; // u64_value = block_sz_
        let mut magic = [0u8; 16];
        apk.read_exact(&mut magic)?;
        if magic != APK_SIGNING_BLOCK_MAGIC {
            Err(bad_apk!("invalid signing block magic"))?;
        }
        let mut signing_blk_sz = 0u64;
        apk.seek(SeekFrom::Current(
            -(u64_val as i64) - (size_of_val(&signing_blk_sz) as i64),
        ))?;
        apk.read_pod(&mut signing_blk_sz)?;
        if signing_blk_sz != u64_val {
            Err(bad_apk!("invalid signing block size"))?;
        }

        // Finally, we are now at the beginning of the id-value pair sequence
        loop {
            apk.read_pod(&mut u64_val)?; // id-value pair length
            if u64_val == signing_blk_sz {
                Err(bad_apk!("cannot find certificate"))?;
            }

            let mut id = 0u32;
            apk.read_pod(&mut id)?;
            if id == SIGNATURE_SCHEME_V2_MAGIC {
                // Skip [signer sequence length] + [1st signer length] + [signed data length]
                apk.seek(SeekFrom::Current((size_of_val(&u32_val) * 3) as i64))?;

                apk.read_pod(&mut u32_val)?; // digest sequence length
                apk.seek(SeekFrom::Current(u32_val as i64))?; // skip all digests

                apk.seek(SeekFrom::Current(size_of_val(&u32_val) as i64))?; // cert sequence length
                apk.read_pod(&mut u32_val)?; // 1st cert length

                let mut cert = vec![0; u32_val as usize];
                apk.read_exact(cert.as_mut())?;
                break Ok(cert);
            } else {
                // Skip this id-value pair
                apk.seek(SeekFrom::Current(
                    u64_val as i64 - (size_of_val(&id) as i64),
                ))?;
            }
        }
    }();
    res.log().unwrap_or(vec![])
}

fn find_apk_path(pkg: &str) -> LoggedResult<Utf8CString> {
    let mut buf = cstr::buf::default();
    Directory::open(cstr!("/data/app"))?.pre_order_walk(|e| {
        if !e.is_dir() {
            return Ok(Skip);
        }
        let name_bytes = e.name().as_bytes();
        if name_bytes.starts_with(pkg.as_bytes()) && name_bytes[pkg.len()] == b'-' {
            // Found the APK path, we can abort now
            e.resolve_path(&mut buf)?;
            return Ok(Abort);
        }
        if name_bytes.starts_with(b"~~") {
            return Ok(Continue);
        }
        Ok(Skip)
    })?;
    if !buf.is_empty() {
        buf.push_str("/base.apk");
    }
    Ok(buf.to_owned())
}

enum Status {
    Installed,
    NotInstalled,
    CertMismatch,
}

pub struct ManagerInfo {
    stub_apk_fd: Option<File>,
    trusted_cert: Vec<u8>,
    repackaged_app_id: i32,
    repackaged_pkg: String,
    repackaged_cert: Vec<u8>,
    tracked_files: BTreeMap<i32, TrackedFile>,
}

impl Default for ManagerInfo {
    fn default() -> Self {
        ManagerInfo {
            stub_apk_fd: None,
            trusted_cert: Vec::new(),
            repackaged_app_id: -1,
            repackaged_pkg: String::new(),
            repackaged_cert: Vec::new(),
            tracked_files: BTreeMap::new(),
        }
    }
}

#[derive(Default)]
struct TrackedFile {
    path: Utf8CString,
    timestamp: Duration,
}

impl TrackedFile {
    fn new(path: Utf8CString) -> TrackedFile {
        let attr = match path.get_attr() {
            Ok(attr) => attr,
            Err(_) => return TrackedFile::default(),
        };
        let timestamp = Duration::new(attr.st.st_ctime as u64, attr.st.st_ctime_nsec as u32);
        TrackedFile { path, timestamp }
    }

    fn is_same(&self) -> bool {
        if self.path.is_empty() {
            return false;
        }
        let attr = match self.path.get_attr() {
            Ok(attr) => attr,
            Err(_) => return false,
        };
        let timestamp = Duration::new(attr.st.st_ctime as u64, attr.st.st_ctime_nsec as u32);
        timestamp == self.timestamp
    }
}

impl ManagerInfo {
    fn get_manager(&mut self, daemon: &MagiskD, user: i32, mut install: bool) -> (i32, &str) {
        let uid = daemon.get_package_uid(user, APP_PACKAGE_NAME);
        return if uid < 0 {
            (-1, "")
        } else {
            (uid, APP_PACKAGE_NAME)
        };
    }
}

impl MagiskD {
    fn get_package_uid(&self, user: i32, pkg: &str) -> i32 {
        let path = cstr::buf::default()
            .join_path(self.app_data_dir())
            .join_path_fmt(user)
            .join_path(pkg);
        path.get_attr()
            .map(|attr| attr.st.st_uid as i32)
            .unwrap_or(-1)
    }

    pub fn get_manager_uid(&self, user: i32) -> i32 {
        let mut info = self.manager_info.lock();
        let (uid, _) = info.get_manager(self, user, false);
        uid
    }

    pub fn get_manager(&self, user: i32, install: bool) -> (i32, String) {
        let mut info = self.manager_info.lock();
        let (uid, pkg) = info.get_manager(self, user, install);
        (uid, pkg.to_string())
    }

    pub fn ensure_manager(&self) {
        let mut info = self.manager_info.lock();
        let _ = info.get_manager(self, 0, true);
    }

    // app_id = app_no + AID_APP_START
    // app_no range: [0, 9999]
    pub fn get_app_no_list(&self) -> BitSet {
        let mut list = BitSet::new();
        let _ = || -> LoggedResult<()> {
            let mut app_data_dir = Directory::open(self.app_data_dir())?;
            // For each user
            loop {
                let entry = match app_data_dir.read()? {
                    None => break,
                    Some(e) => e,
                };
                let mut user_dir = match entry.open_as_dir() {
                    Err(_) => continue,
                    Ok(dir) => dir,
                };
                // For each package
                loop {
                    match user_dir.read()? {
                        None => break,
                        Some(e) => {
                            let mut entry_path = cstr::buf::default();
                            e.resolve_path(&mut entry_path)?;
                            let attr = entry_path.get_attr()?;
                            let app_id = to_app_id(attr.st.st_uid as i32);
                            if (AID_APP_START..=AID_APP_END).contains(&app_id) {
                                let app_no = app_id - AID_APP_START;
                                list.insert(app_no as usize);
                            }
                        }
                    }
                }
            }
            Ok(())
        }();
        list
    }
}
