docker rm -f edgereach-emu >/dev/null 2>&1
docker run -d --name edgereach-emu --device /dev/kvm -v android-sdk:/sdk -e ANDROID_HOME=/sdk -e ANDROID_SDK_ROOT=/sdk gradle:8.14.3-jdk21 sleep infinity >/dev/null
docker exec edgereach-emu bash -c '
apt-get -qq update >/dev/null 2>&1; apt-get -qq install -y libpulse0 libnss3 libxcomposite1 libxcursor1 libxdamage1 libxi6 libxtst6 libasound2t64 libgl1 libegl1 libxkbfile1 libxkbcommon0 libdbus-1-3 libxml2-utils >/dev/null 2>&1
echo no | /sdk/cmdline-tools/latest/bin/avdmanager create avd -n t36 -k "system-images;android-36;google_apis;x86_64" -d pixel_7 >/dev/null
sed -i "s/^hw.keyboard=.*/hw.keyboard=no/" ~/.android/avd/t36.avd/config.ini'
docker exec -d edgereach-emu bash -c '/sdk/emulator/emulator -avd t36 -no-window -no-audio -no-boot-anim -gpu swiftshader_indirect -accel on -memory 4096 -cores 4 -no-snapshot > /tmp/emu.log 2>&1'
docker exec edgereach-emu bash -c 'A=/sdk/platform-tools/adb; $A wait-for-device; for i in $(seq 1 90); do [ "$($A shell getprop sys.boot_completed 2>/dev/null | tr -d "\r")" = 1 ] && echo booted && break; sleep 5; done'
