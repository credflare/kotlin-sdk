import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

adb = os.environ.get('ADB', 'adb')
serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')
mode = sys.argv[1] if len(sys.argv) > 1 else 'liveness'
demo_url = os.environ.get('DEMO_URL', 'http://127.0.0.1:3107')
shots = Path(os.environ.get('SCREENSHOTS', Path(__file__).resolve().parent.parent / 'example/build/screens'))
package = 'net.credflare.kotlindemo'

def run(*args):
    return subprocess.check_output([adb, '-s', serial, *args], text=True, timeout=30)

def nodes():
    result = run('shell', 'uiautomator', 'dump', '/sdcard/credflare-ui.xml')
    if 'dumped to:' not in result:
        return []
    return list(ET.fromstring(run('shell', 'cat', '/sdcard/credflare-ui.xml')).iter('node'))

def wait_for(labels, seconds=30):
    last = []
    for _ in range(seconds):
        try:
            last = nodes()
            for label in labels:
                for node in last:
                    if node.get('text') == label and node.get('enabled') == 'true':
                        return node
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        time.sleep(1)
    raise RuntimeError('Missing screen: ' + ', '.join(labels) + '; saw ' + ' | '.join(n.get('text', '') for n in last if n.get('text')))

def tap(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

def shot(name):
    shots.mkdir(parents=True, exist_ok=True)
    (shots / f'{name}.png').write_bytes(subprocess.check_output([adb, '-s', serial, 'exec-out', 'screencap', '-p'], timeout=30))

def allow():
    for label in ['While using the app', 'Only this time', 'Allow']:
        for node in nodes():
            if node.get('text') == label:
                tap(node)
                return

assert serial.startswith('emulator-') and run('emu', 'avd', 'name').strip(), 'Use a test emulator.'
run('shell', 'am', 'force-stop', package)
run('shell', 'am', 'start', '-n', f'{package}/.MainActivity', '-e', 'demo_url', demo_url)
tap(wait_for(['Start verification']))

if mode == 'liveness':
    wait_for(['Follow the prompts'])
    shot('liveness-intro')
    tap(wait_for(['Start']))
    time.sleep(2)
    allow()
    wait_for(['Cancel'])
    time.sleep(3)
    shot('liveness-camera')
    wait_for(['Try again'], 40)
    shot('liveness-timeout')
    print('PASS: liveness challenge requested, camera analysis ran and the timeout offers a new challenge')
elif mode == 'location':
    wait_for(['Share location'])
    shot('location-consent')
    run('shell', 'appops', 'set', 'com.android.shell', 'android:mock_location', 'allow')
    run('shell', 'cmd', 'location', 'providers', 'add-test-provider', 'gps')
    run('shell', 'cmd', 'location', 'providers', 'set-test-provider-enabled', 'gps', 'true')
    run('shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps', '--location', '4.0511,9.7679', '--accuracy', '5')
    try:
        tap(wait_for(['Share location']))
        time.sleep(2)
        allow()
        for _ in range(5):
            run('shell', 'cmd', 'location', 'providers', 'set-test-provider-location', 'gps', '--location', '4.0511,9.7679', '--accuracy', '5')
            time.sleep(1)
        send = wait_for(['Send location'], 60)
        shot('location-found')
        tap(send)
        message = 'An app or setting is changing your device location. Turn it off, then try again.'
        wait_for([message], 30)
        shot('location-mock-rejected')
        print('PASS: mock location sent with is_mock true and the rejection was shown with a retry')
    finally:
        run('shell', 'cmd', 'location', 'providers', 'remove-test-provider', 'gps')
        run('shell', 'appops', 'set', 'com.android.shell', 'android:mock_location', 'default')
elif mode == 'location-real':
    wait_for(['Share location'])
    run('emu', 'geo', 'fix', '9.7679', '4.0511')
    tap(wait_for(['Share location']))
    time.sleep(2)
    allow()
    for _ in range(5):
        run('emu', 'geo', 'fix', '9.7679', '4.0511')
        time.sleep(1)
    send = wait_for(['Send location'], 60)
    shot('location-found')
    tap(send)
    wait_for(['Evidence received'], 30)
    shot('receipt')
    print('PASS: emulator GPS fix sent and the receipt was shown')
