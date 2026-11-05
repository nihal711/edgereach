A=/sdk/platform-tools/adb
en() { $A shell settings get secure enabled_accessibility_services | tr -d "\r"; }
focus() { $A shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*u0 //;s/}//'; }
lr() { echo "L=$($A shell settings get secure back_gesture_inset_scale_left | tr -d '\r') R=$($A shell settings get secure back_gesture_inset_scale_right | tr -d '\r')"; }
net() { $A shell am start -W -a android.settings.WIRELESS_SETTINGS >/dev/null; sleep 1.5; }
echo "before: $(lr)"
$A shell input tap 926 1184; sleep 1.5; echo "after tapping switch: $(lr)"
$A exec-out screencap -p > /tmp/f6.png
net; echo "start: $(focus)"
$A shell input swipe 1078 500 900 500 200; sleep 1.5; echo "right edge swipe at y=500 (outside handle): $(focus)"
$A shell input swipe 2 500 180 500 200; sleep 1.5; echo "left edge swipe at y=500 (outside handle): $(focus)"
$A shell input swipe 1075 1500 930 1500 180; sleep 1.5; echo "right handle swipe (Back): $(focus)"
echo "== service switched off -> restored?"
$A shell settings put secure enabled_accessibility_services none; sleep 1.5; echo "while off: $(lr)"
net; $A shell input swipe 1078 500 900 500 200; sleep 1.5; echo "system back works while off: $(focus)"
sleep 5; echo "after auto-repair: [$(en)] $(lr)"
echo "== force-stop -> restored on next start?"
$A shell am force-stop dev.nihal.thumbedge; sleep 1; echo "right after force-stop: $(lr)"
$A shell am start -W -n dev.nihal.thumbedge/.MainActivity >/dev/null; sleep 4; echo "after app opened: [$(en)] $(lr)"
echo "== turn option off"
$A shell input tap 480 1416; sleep 1.5; $A shell input swipe 540 2000 540 600 1200; sleep 0.5; $A shell input swipe 540 2000 540 600 1200; sleep 1
$A shell input tap 926 1184; sleep 1.5; echo "after switching off: $(lr)"
$A exec-out screencap -p > /tmp/f7.png
net; $A shell input swipe 1078 500 900 500 200; sleep 1.5; echo "system back works again: $(focus)"
echo "== crashes"; $A logcat -d -b crash | head
