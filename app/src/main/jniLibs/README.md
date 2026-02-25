# jniLibs — Pre-built eSpeak NG Shared Libraries

Place your compiled `libespeak-ng.so` files here, organized by ABI:

```
jniLibs/
├── arm64-v8a/
│   └── libespeak-ng.so
├── armeabi-v7a/
│   └── libespeak-ng.so
└── x86_64/
    └── libespeak-ng.so
```

## How to build libespeak-ng.so

See the README.md at the root of this project for full cross-compilation
instructions using the Android NDK CMake toolchain.

Quick reference:
```bash
git clone https://github.com/espeak-ng/espeak-ng.git
cd espeak-ng
mkdir build-arm64 && cd build-arm64
cmake .. \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DCMAKE_BUILD_TYPE=Release \
  -DBUILD_SHARED_LIBS=ON \
  -DESPEAK_BUILD_TESTS=OFF
make -j4
```

The output `libespeak-ng.so` goes into `jniLibs/arm64-v8a/`.
Repeat for `armeabi-v7a` and `x86_64`.
