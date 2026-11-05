A=/sdk/platform-tools/adb
focus() { $A shell dumpsys window | grep -m1 mCurrentFocus | sed 's/.*u0 //;s/}//'; }
shade() { $A shell dumpsys window | grep -c "NotificationShade.*mHasSurface=true\|mIsExpanded=true" ; }
settings_sub() { $A shell am start -W -a android.settings.WIRELESS_SETTINGS >/dev/null; sleep 1.5; }
echo "== 1 straight short (Back) from Wi-Fi settings"
settings_sub; echo "before: $(focus)"; $A shell input swipe 1075 1500 930 1500 180; sleep 1.5; echo "after:  $(focus)"
echo "== 2 diagonal up short (Home)"
settings_sub; $A shell input swipe 1075 1500 965 1420 180; sleep 1.5; echo "after:  $(focus)"
echo "== 3 diagonal down short (Notifications)"
$A shell input swipe 1075 1400 965 1480 180; sleep 1.5; $A shell dumpsys statusbar 2>/dev/null | grep -m2 -iE "panel.*expan|mExpandedVisible|expanded" ; $A exec-out screencap -p > /tmp/s3.png
$A shell cmd statusbar collapse; sleep 1
echo "== 4 diagonal up long (Recents)"
$A shell am start -W -n dev.nihal.thumbedge/.MainActivity >/dev/null; sleep 1
$A shell input swipe 1075 1700 760 1450 350; sleep 2; echo "after:  $(focus)"; $A exec-out screencap -p > /tmp/s4.png
$A shell input keyevent KEYCODE_HOME; sleep 1
echo "== 5 straight long (Previous app): open Settings then ThumbEdge, expect Settings"
settings_sub; $A shell am start -W -n dev.nihal.thumbedge/.MainActivity >/dev/null; sleep 1.5; echo "before: $(focus)"
$A shell input swipe 1075 1500 650 1500 350; sleep 2; echo "after:  $(focus)"
echo "== 6 left handle straight short (Back) from Wi-Fi settings"
settings_sub; $A shell input swipe 5 1500 150 1500 180; sleep 1.5; echo "after:  $(focus)"
echo "== 7 cancel: swipe in then back to edge (expect no change)"
settings_sub; $A shell input motionevent DOWN 1075 1500; $A shell input motionevent MOVE 1000 1500; $A shell input motionevent MOVE 930 1500; sleep 0.3; $A exec-out screencap -p > /tmp/s7.png; $A shell input motionevent MOVE 1070 1500; $A shell input motionevent UP 1070 1500; sleep 1.5; echo "after:  $(focus)"
echo "== 8 tap pass-through on a Settings row under the right handle"
$A shell am start -W -a android.settings.SETTINGS >/dev/null; sleep 1.5; $A exec-out screencap -p > /tmp/s8a.png; $A shell input tap 1060 1400; sleep 2; echo "after tap: $(focus)"; $A exec-out screencap -p > /tmp/s8b.png
echo "== crashes"; $A logcat -d -b crash | head -20
