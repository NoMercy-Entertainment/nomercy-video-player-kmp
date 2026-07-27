#!/usr/bin/env bash
#
# Builds libass and its dependencies for every Apple target this module has:
# iOS device, iOS simulator, tvOS device and tvOS simulator. One XCFramework
# per library lands in out/.
#
# It descends from the app's build-ios.sh and differs in two ways that matter.
#
# The first is tvOS. Pointing the tvOS targets at the iOS slice looks like it
# should work — same architecture, same toolchain — and the linker refuses it
# outright: "building for 'tvOS-simulator', but linking in object file built for
# 'iOS-simulator'". A slice carries its platform, not only its instruction set.
#
# The second is the sources. The app's copy expects freetype, fribidi, harfbuzz
# and libass to be sitting beside it, already cloned by hand at whatever revision
# the last person happened to have. This clones them at pinned tags, so the
# archive it produces can be rebuilt byte-for-byte reasons rather than hoped at.
#
# Requires a full Xcode (not just Command Line Tools) and:
#   brew install autoconf automake libtool nasm pkgconf cmake
#
# Expect an hour or more. It builds four libraries across five slices.
#
# Naming libraries builds only those and re-bundles everything, which is what a
# version bump on one dependency wants:
#
#   ./build-apple.sh libass
set -euo pipefail

WANTED="${*:-freetype fribidi harfbuzz libass}"

wanted() {
    case " $WANTED " in
        *" $1 "*) return 0 ;;
        *) return 1 ;;
    esac
}

ROOT="$(cd "$(dirname "$0")" && pwd)"
SRC="$ROOT/src"
OUT="$ROOT/out"
mkdir -p "$SRC" "$OUT"

# Pinned, and the pin is the point. A native library that changed under a build
# is the kind of supply-chain problem that shows up as a crash on someone else's
# television.
FREETYPE_REPO="https://gitlab.freedesktop.org/freetype/freetype.git"
FREETYPE_TAG="VER-2-13-3"
FRIBIDI_REPO="https://github.com/fribidi/fribidi.git"
FRIBIDI_TAG="v1.0.16"
HARFBUZZ_REPO="https://github.com/harfbuzz/harfbuzz.git"
HARFBUZZ_TAG="8.5.0"
LIBASS_REPO="https://github.com/libass/libass.git"
LIBASS_TAG="0.17.5"

export PATH="/opt/homebrew/bin:$PATH"
export DEVELOPER_DIR="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

# Homebrew installs autotools as glibtoolize/glibtool to avoid colliding with
# Xcode's own libtool. autoreconf finds them through LIBTOOL/LIBTOOLIZE, but some
# autogen.sh scripts hardcode the plain names — symlinking is what always works.
TOOLS="$ROOT/.tools"
mkdir -p "$TOOLS"
for cmd in libtool libtoolize; do
    if ! command -v "$cmd" >/dev/null 2>&1 && command -v "g$cmd" >/dev/null 2>&1; then
        ln -sf "$(command -v "g$cmd")" "$TOOLS/$cmd"
    fi
done
export PATH="$TOOLS:$PATH"

fetch() {
    local dir="$1" repo="$2" tag="$3"
    if [ -d "$SRC/$dir/.git" ]; then
        local have
        have="$(git -C "$SRC/$dir" describe --tags --always)"
        if [ "$have" = "$tag" ]; then
            echo "== $dir already at $tag"
            return
        fi
        # The pin moved. Fetching the new tag into the existing clone rather
        # than re-cloning keeps the other libraries' builds intact, which is the
        # difference between a ten-minute bump and an hour.
        echo "== $dir moving from $have to $tag"
        git -C "$SRC/$dir" fetch --depth 1 origin "refs/tags/$tag:refs/tags/$tag"
        git -C "$SRC/$dir" checkout -q --force "$tag"
        git -C "$SRC/$dir" clean -qfdx
        return
    fi
    echo "== cloning $dir at $tag"
    git clone --depth 1 --branch "$tag" "$repo" "$SRC/$dir"
}

# What gets built, and the three facts that differ: the SDK to compile against,
# the architecture, and a name for the prefix it installs into.
#
# The iOS simulator is built twice. An Apple-silicon Mac needs the arm64 slice
# and an Intel one needs x86_64, and the module declares both iosSimulatorArm64
# and iosX64 as targets — dropping either would quietly remove a platform from
# the library to save an hour of build time.
slice_sdks() {
    echo "iphoneos arm64 ios"
    echo "iphonesimulator arm64 ios-simulator-arm64"
    echo "iphonesimulator x86_64 ios-simulator-x86_64"
    echo "appletvos arm64 tvos"
    echo "appletvsimulator arm64 tvos-simulator"
}

# The triple carries the platform as well as the architecture, and that is the
# whole reason tvOS needs its own slices: an object built for iOS-simulator is
# refused by a tvOS-simulator link even though the instruction set is identical.
triple_for() {
    case "$1" in
        ios)                  echo "arm64-apple-ios14.0" ;;
        ios-simulator-arm64)  echo "arm64-apple-ios14.0-simulator" ;;
        ios-simulator-x86_64) echo "x86_64-apple-ios14.0-simulator" ;;
        tvos)                 echo "arm64-apple-tvos14.0" ;;
        tvos-simulator)       echo "arm64-apple-tvos14.0-simulator" ;;
    esac
}

cmake_system_for() {
    case "$1" in
        ios*) echo "iOS" ;;
        tvos*) echo "tvOS" ;;
    esac
}

build_arch() {
    local lib="$1" arch="$2" sdk="$3" platform="$4" prefix="$5"
    local sdk_path target
    sdk_path="$(xcrun --sdk "$sdk" --show-sdk-path)"
    target="$(triple_for "$platform")"

    export CC="$(xcrun --sdk "$sdk" --find clang)"
    export AR="$(xcrun --sdk "$sdk" --find ar)"
    export RANLIB="$(xcrun --sdk "$sdk" --find ranlib)"
    export STRIP="$(xcrun --sdk "$sdk" --find strip)"
    export NM="$(xcrun --sdk "$sdk" --find nm)"

    local common="-arch $arch -isysroot $sdk_path -target $target"
    export CFLAGS="$common -O2 -fPIC"
    export CXXFLAGS="$common -O2 -fPIC"
    export LDFLAGS="$common"

    # autoconf otherwise tries to run what it just built. For the simulator
    # slices the output IS runnable on this host, which makes it cache the wrong
    # answers for everything that follows.
    export cross_compiling=yes

    # arm-apple-darwin rather than the real triple: autoconf reads it as
    # different from the build host and skips those run checks entirely.
    local host="arm-apple-darwin"

    # Every script goes through /bin/sh explicitly. On recent macOS, Gatekeeper
    # stalls for minutes on the first execve of an unsigned shebanged script
    # while it asks Apple about notarization; naming the interpreter skips it.
    pushd "$SRC/$lib" >/dev/null

    if [ "$lib" = "freetype" ]; then
        [ -f builds/unix/configure ] || /bin/sh ./autogen.sh
    else
        [ -f configure ] || NOCONFIGURE=1 /bin/sh ./autogen.sh
    fi

    local extra_flags=""
    case "$lib" in
        libass)
            extra_flags="--disable-fontconfig --enable-coretext --disable-libunibreak --disable-asm"
            ;;
        freetype)
            # freetype auto-enables Apple's HVF backend when it finds
            # hvf/Scaler.h in the sysroot, which leaves _HVF_* undefined at app
            # link time — HVF is a PrivateFramework a shipped app cannot link.
            export ac_cv_header_hvf_Scaler_h=no
            extra_flags="--without-harfbuzz --without-png --without-zlib --without-bzip2 --without-brotli"
            ;;
        fribidi)
            extra_flags="--disable-debug --with-glib=no --disable-deprecated"
            ;;
    esac

    if [ "$lib" = "freetype" ]; then
        # freetype's autotools only supports in-tree builds: the top-level
        # configure runs `make setup unix`, which re-invokes the nested configure
        # with arguments taken from the environment rather than from the command
        # line it was given.
        make distclean >/dev/null 2>&1 || true
        /bin/sh ./configure --host="$host" --prefix="$prefix" \
            --enable-static --disable-shared --disable-dependency-tracking $extra_flags
        make -j"$(sysctl -n hw.ncpu)"
        make install
        make distclean >/dev/null 2>&1 || true
    else
        local build_dir="$SRC/$lib/build-${sdk}-${arch}"
        rm -rf "$build_dir"
        mkdir -p "$build_dir"
        cd "$build_dir"

        # PKG_CONFIG_LIBDIR restricted to our own prefix. Without it pkg-config
        # falls back to Homebrew's and libass links the host's harfbuzz, leaving
        # undefined _hb_* symbols nothing on the device can satisfy.
        /bin/sh ../configure --host="$host" --prefix="$prefix" \
            --enable-static --disable-shared --disable-dependency-tracking \
            PKG_CONFIG_LIBDIR="$prefix/lib/pkgconfig" \
            PKG_CONFIG_PATH="$prefix/lib/pkgconfig" \
            $extra_flags

        if [ "$lib" = "fribidi" ]; then
            build_fribidi
        else
            make -j"$(sysctl -n hw.ncpu)"
            make install
        fi
    fi
    popd >/dev/null
}

# fribidi generates its Unicode tables with tools that have to RUN here, while
# everything else cross-compiles. And its parallel dependency graph is wrong:
# the generator sources include a version header that only appears as a side
# effect of running another generator, with nothing declaring that order.
build_fribidi() {
    local saved_cc="$CC" saved_cflags="$CFLAGS" saved_ldflags="$LDFLAGS"
    export CC=/usr/bin/clang
    export CFLAGS="-O2"
    export LDFLAGS=""
    unset MAKEFLAGS

    make -C lib fribidi-unicode-version.h
    test -f lib/fribidi-unicode-version.h || {
        echo "fribidi: the version header was not generated" >&2
        exit 1
    }
    make -C gen.tab -j"$(sysctl -n hw.ncpu)"

    export CC="$saved_cc"
    export CFLAGS="$saved_cflags"
    export LDFLAGS="$saved_ldflags"
    make -C lib -j"$(sysctl -n hw.ncpu)"
    make -C lib install
    # Explicitly, so the doc and test directories never run — doc/ treats a
    # missing c2man as a hard error and none of it is wanted here.
    make install-pkgconfigDATA install-includeHEADERS 2>/dev/null || true
}

build_lib() {
    local lib="$1"
    while read -r sdk arch platform; do
        local prefix="$OUT/prefix/${platform}"
        mkdir -p "$prefix"
        echo "== $lib for $platform"
        build_arch "$lib" "$arch" "$sdk" "$platform" "$prefix"
    done < <(slice_sdks)
}

# harfbuzz builds through CMake, whose cross-compile knobs are simpler than
# meson's for a static-only build. libass 0.17 needs it at link time.
build_harfbuzz() {
    while read -r sdk arch platform; do
        local prefix="$OUT/prefix/${platform}"
        local build_dir="$SRC/harfbuzz/build-${platform}"
        local sdk_path system
        sdk_path="$(xcrun --sdk "$sdk" --show-sdk-path)"
        system="$(cmake_system_for "$platform")"
        rm -rf "$build_dir"
        mkdir -p "$build_dir"

        # The autotools builds leak CFLAGS and friends with another sysroot
        # baked in, and CMake picks them up from the environment and then fails
        # its own "compile a tiny program" probe against two sysroots at once.
        unset CFLAGS CXXFLAGS LDFLAGS CC AR RANLIB STRIP NM

        echo "== harfbuzz for $platform"
        cmake -S "$SRC/harfbuzz" -B "$build_dir" \
            -DCMAKE_BUILD_TYPE=Release \
            -DCMAKE_INSTALL_PREFIX="$prefix" \
            -DCMAKE_OSX_SYSROOT="$sdk_path" \
            -DCMAKE_OSX_ARCHITECTURES="$arch" \
            -DCMAKE_OSX_DEPLOYMENT_TARGET=14.0 \
            -DCMAKE_SYSTEM_NAME="$system" \
            -DBUILD_SHARED_LIBS=OFF \
            -DHB_HAVE_FREETYPE=ON \
            -DHB_HAVE_CORETEXT=ON \
            -DHB_BUILD_UTILS=OFF \
            -DHB_BUILD_TESTS=OFF \
            -DHB_BUILD_SUBSET=OFF \
            -DCMAKE_PREFIX_PATH="$prefix" \
            -DFREETYPE_INCLUDE_DIRS="$prefix/include/freetype2" \
            -DFREETYPE_LIBRARY="$prefix/lib/libfreetype.a"
        cmake --build "$build_dir" --target install --parallel "$(sysctl -n hw.ncpu)"

        # harfbuzz's CMake install writes no .pc file, and libass finds it
        # through pkg-config, so one is written by hand.
        mkdir -p "$prefix/lib/pkgconfig"
        cat > "$prefix/lib/pkgconfig/harfbuzz.pc" <<EOF
prefix=$prefix
exec_prefix=\${prefix}
libdir=\${prefix}/lib
includedir=\${prefix}/include

Name: harfbuzz
Description: HarfBuzz text shaping library
Version: $HARFBUZZ_TAG
Libs: -L\${libdir} -lharfbuzz -lc++
Cflags: -I\${includedir}/harfbuzz
EOF
    done < <(slice_sdks)
}

make_xcframework() {
    local lib="$1" archive="lib$1.a"
    rm -rf "$OUT/$lib.xcframework"

    # xcodebuild refuses two libraries for the same platform, so the two iOS
    # simulator architectures are fattened into one archive first.
    local universal="$OUT/prefix/ios-simulator/lib"
    mkdir -p "$universal"
    lipo -create         "$OUT/prefix/ios-simulator-arm64/lib/$archive"         "$OUT/prefix/ios-simulator-x86_64/lib/$archive"         -output "$universal/$archive"

    local args=()
    for platform in ios ios-simulator tvos tvos-simulator; do
        args+=(-library "$OUT/prefix/$platform/lib/$archive")
        # Headers on the ass framework only. Every library installs into its own
        # prefix but they share include/ names, and bundling all of them into
        # each framework made several emit the same header — "Multiple commands
        # produce" at app build time.
        if [ "$lib" = "ass" ]; then
            args+=(-headers "$OUT/prefix/ass-headers")
        fi
    done

    if [ "$lib" = "ass" ]; then
        rm -rf "$OUT/prefix/ass-headers"
        mkdir -p "$OUT/prefix/ass-headers"
        cp -R "$OUT/prefix/ios/include/ass" "$OUT/prefix/ass-headers/ass"
    fi

    xcodebuild -create-xcframework "${args[@]}" -output "$OUT/$lib.xcframework"
}

fetch freetype "$FREETYPE_REPO" "$FREETYPE_TAG"
fetch fribidi  "$FRIBIDI_REPO"  "$FRIBIDI_TAG"
fetch harfbuzz "$HARFBUZZ_REPO" "$HARFBUZZ_TAG"
fetch libass   "$LIBASS_REPO"   "$LIBASS_TAG"

wanted freetype && build_lib freetype
wanted fribidi  && build_lib fribidi
wanted harfbuzz && build_harfbuzz
wanted libass   && build_lib libass

make_xcframework freetype
make_xcframework fribidi
make_xcframework harfbuzz
make_xcframework ass

# Swift resolves `import Libass` through a modulemap inside the framework's
# Headers/, and -create-xcframework does not emit one. Every slice needs it, and
# the slice directory names vary with the arch set they were built from.
for slice_headers in "$OUT/ass.xcframework"/*/Headers; do
    cat > "$slice_headers/module.modulemap" <<'EOF'
module Libass {
    header "ass/ass.h"
    header "ass/ass_types.h"
    export *
}
EOF
done

echo "XCFrameworks ready in $OUT"
for framework in "$OUT"/*.xcframework; do
    echo "  $(basename "$framework"): $(ls "$framework" | tr '\n' ' ')"
done
