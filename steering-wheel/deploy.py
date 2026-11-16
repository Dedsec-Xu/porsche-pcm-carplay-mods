"""Install only the verified combined package; keep the preceding audio JAR intact."""
import hashlib
import re
import socket
import sys
import time
from pathlib import Path

root = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(root))
from pcm_transport import MinimalTelnet, HOST, PORT, USER, PASSWORD

folder = Path(__file__).parent
remote = '/mnt/app/eso/hmi/lsd/jars/pcm-prompt-level.jar'
backup = remote + '.before-joker'
expected = 'e0826c15189b46d8493567c3360bbd9785c28e93b614fa8e94a7e08c451afff6'
c = MinimalTelnet(HOST, PORT)
c.read_until(b'login:'); c.send_line(USER)
c.read_until(b'Password:'); c.send_line(PASSWORD); c.read_until(b'#')

def command(script):
    token = 'RESULT' + str(time.time_ns())
    c.send_line(script + '\necho ' + token + ':$?')
    c.read_until(b'\n' + token.encode() + b':', 45)
    deadline = time.monotonic() + 5
    while time.monotonic() < deadline:
        data = bytes(c.text).replace(b'\x00', b'').replace(b'\r', b'')
        result = re.search(rb'\n' + token.encode() + rb':(\d+)\n', data)
        if result:
            if int(result.group(1)): raise RuntimeError('Remote operation failed: ' + script)
            return
        try: c._feed(c.sock.recv(4096))
        except socket.timeout: pass
    raise TimeoutError('No command status')

def ftp(lines):
    command("ftp -n 172.16.250.100 2121 <<'EOF'\nuser anonymous pcm\nbinary\n" + '\n'.join(lines) + '\nquit\nEOF')

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

try:
    action = sys.argv[1:]
    if action == ['install']:
        stamp = str(time.time_ns())
        current = folder / ('current-' + stamp + '.local.jar')
        staged = folder / ('staged-' + stamp + '.local.jar')
        installed = folder / ('installed-' + stamp + '.local.jar')
        candidate = folder / 'pcm-audio-joker.jar'
        ftp(['put ' + remote + ' steering-wheel/' + current.name])
        if sha(current) != expected: raise RuntimeError('Existing audio JAR differs from verified baseline')
        command('mount -uw /mnt/app')
        command('test ! -e ' + backup)
        command('cp -p ' + remote + ' ' + backup)
        ftp(['get steering-wheel/pcm-audio-joker.jar ' + remote + '.pending-joker',
             'put ' + remote + '.pending-joker steering-wheel/' + staged.name])
        if sha(staged) != sha(candidate): raise RuntimeError('Staged readback mismatch')
        command('chmod 644 ' + remote + '.pending-joker')
        command('mv ' + remote + '.pending-joker ' + remote)
        command('sync')
        ftp(['put ' + remote + ' steering-wheel/' + installed.name])
        if sha(installed) != sha(candidate): raise RuntimeError('Installed readback mismatch; rollback required')
        print('Installed and readback verified: ' + sha(candidate))
        print('Previous audio-only JAR preserved at ' + backup)
    elif action == ['rollback']:
        command('test -f ' + backup)
        command('mount -uw /mnt/app')
        command('cp -p ' + backup + ' ' + remote + '.restore-joker')
        command('mv ' + remote + '.restore-joker ' + remote)
        command('sync')
        print('Restored previous audio-only JAR')
    else:
        raise SystemExit('Usage: deploy.py install|rollback')
    print('Reboot required')
finally:
    c.send_line('exit'); c.sock.close()
