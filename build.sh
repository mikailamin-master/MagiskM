#!/data/data/com.termux/files/usr/bin/sh

repo="mikailamin-master/MagiskM"
tag="native_d"

native_lib_dir="app/src/main/assets/libs"
commit_file="$native_lib_dir/last_native_commit.txt"

api_url="https://api.github.com/repos/$repo/releases/tags/$tag"

zip_url="https://github.com/$repo/releases/download/$tag/native_debug.zip"
zip_file="$TMPDIR/native_debug.zip"

echo "[I] Checking latest native release commit..."

latest_commit=$(curl -s "$api_url" | sed -n '
    /"body":/{
        s/.*Commit: \([a-f0-9]*\).*/\1/p
    }
')

if [ -z "$latest_commit" ]; then
    echo "[E] Failed to get latest commit id"
    exit 1
fi

need_download=0

if [ ! -d "$native_lib_dir" ]; then
    echo "[W] libs folder not found"
    need_download=1
else
    if [ -z "$(ls -A "$native_lib_dir")" ]; then
        echo "[W] libs folder empty"
        need_download=1
    else
        if [ ! -f "$commit_file" ]; then
            echo "[W] commit file missing"
            need_download=1
        else
            saved_commit=$(cat "$commit_file")

            if [ "$saved_commit" != "$latest_commit" ]; then
                echo "[W] commit mismatch"
                echo "[I] local: $saved_commit"
                echo "[I] remote: $latest_commit"
                need_download=1
            else
                echo "[I] Native libs already up to date"
            fi
        fi
    fi
fi

if [ "$need_download" -eq 1 ]; then
    mkdir -p "$native_lib_dir" || {
        echo "[E] Failed to create libs folder"
        exit 1
    }

    echo "[I] Downloading native libs..."

    curl -L "$zip_url" -o "$zip_file" || {
        echo "[E] Download failed"
        exit 1
    }

    echo "[I] Extracting native libs..."

    unzip -qo "$zip_file" -d "$native_lib_dir" || {
        echo "[E] Extract failed"
        exit 1
    }

    echo "[I] Saving commit id..."

    echo "$latest_commit" > "$commit_file" || {
        echo "[E] Failed to save commit file"
        exit 1
    }

    rm -f "$zip_file"

    echo "[I] Done. Native libs updated to: $latest_commit"
fi

echo "[I] Setting gradlew permission..."
chmod +x gradlew || {
    echo "[E] chmod failed"
    exit 1
}

echo "[I] Running gradle build..."
./gradlew assembleDebug || {
    echo "[E] Gradle build failed"
    exit 1
}

echo "[I] Build finished successfully"