use crate::consts::APP_PACKAGE_NAME;
use crate::daemon::{AID_APP_END, AID_APP_START, MagiskD, to_app_id};
use base::{Directory, FsPathBuilder, LoggedResult, cstr};
use bit_set::BitSet;

pub struct ManagerInfo {}

impl Default for ManagerInfo {
    fn default() -> Self {
        ManagerInfo {}
    }
}

impl ManagerInfo {
    fn get_manager<'a>(&'a mut self, daemon: &MagiskD, user: i32) -> (i32, &'a str) {
        let uid = daemon.get_package_uid(user, APP_PACKAGE_NAME);
        if uid < 0 {
            (-1, "")
        } else {
            (uid, APP_PACKAGE_NAME)
        }
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
        let (uid, _) = info.get_manager(self, user);
        uid
    }

    pub fn get_manager(&self, user: i32) -> (i32, String) {
        let mut info = self.manager_info.lock();
        let (uid, pkg) = info.get_manager(self, user);
        (uid, pkg.to_string())
    }

    pub fn ensure_manager(&self) {
        let mut info = self.manager_info.lock();
        let _ = info.get_manager(self, 0);
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
