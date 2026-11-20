import os
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

adb = os.environ.get('ADB', 'adb')
serial = os.environ.get('ANDROID_SERIAL', 'emulator-5554')

def run(*args):
    return subprocess.check_output([adb, '-s', serial, *args], text=True, timeout=20)

def nodes():
    result = run('shell', 'uiautomator', 'dump', '/sdcard/credflare-ui.xml')
    if 'dumped to:' not in result:
        return []
    return list(ET.fromstring(run('shell', 'cat', '/sdcard/credflare-ui.xml')).iter('node'))

def wait_for(labels):
    last = []
    for _ in range(30):
        try:
            last = nodes()
            for label in labels:
                for node in last:
                    if node.get('text') == label and node.get('enabled') == 'true':
                        return node
        except (subprocess.SubprocessError, ET.ParseError):
            pass
        time.sleep(1)
    raise RuntimeError('Missing screen: ' + ', '.join(labels) + '; saw ' + ', '.join(n.get('text', '') for n in last if n.get('text')))

def tap(node):
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    run('shell', 'input', 'tap', str((left + right) // 2), str((top + bottom) // 2))

assert serial.startswith('emulator-') and run('emu', 'avd', 'name').strip(), 'Use a test emulator with a software front camera.'
for port in [3107, 8018]:
    run('reverse', f'tcp:{port}', f'tcp:{port}')
run('shell', 'am', 'start', '-n', 'net.credflare.kotlindemo/.MainActivity')
tap(wait_for(['Start verification']))
tap(wait_for(['Open front camera']))
next_step = wait_for(['While using the app', 'Take selfie'])
if next_step.get('text') == 'While using the app':
    tap(next_step)
    next_step = wait_for(['Take selfie'])
tap(next_step)
wait_for(['Review your selfie'])
tap(wait_for(['Send selfie']))
wait_for(['Evidence received'])
output = Path(__file__).resolve().parent.parent / 'example/build/facial-received.png'
output.parent.mkdir(parents=True, exist_ok=True)
output.write_bytes(subprocess.check_output([adb, '-s', serial, 'exec-out', 'screencap', '-p'], timeout=20))
print('PASS: native selfie session, software front camera, preview, biometric upload, facial submission and collection receipt')
