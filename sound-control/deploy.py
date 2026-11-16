import hashlib, os, re, sys, time
from pathlib import Path

base = Path(__file__).parent.parent
exec((base / 'pcm_transport.py').read_text())
sys.stdout.reconfigure(encoding='utf-8', errors='replace')
jar_dir = '/mnt/app/eso/hmi/lsd/jars'
apps = '/mnt/app/eso/bin/apps'
stamp = str(time.time_ns())
c = MinimalTelnet(HOST, PORT)
c.read_until(b'login:'); c.send_line(USER)
c.read_until(b'Password:'); c.send_line(PASSWORD); c.read_until(b'#')

def command(script):
    token = 'RESULT' + str(time.time_ns())
    c.send_line(script + '\necho ' + token + ':$?')
    raw = c.read_until(b'\n' + token.encode() + b':', 45)
    end = time.monotonic() + 5
    while True:
        result = bytes(c.text).replace(b'\x00', b'').replace(b'\r', b'')
        match = re.search(rb'\n' + token.encode() + rb':(\d+)\n', result)
        if match:
            if int(match.group(1)): raise RuntimeError('remote command failed: ' + script)
            return
        if time.monotonic() > end: raise TimeoutError('exit status missing')
        try: c._feed(c.sock.recv(4096))
        except socket.timeout: pass

def ftp(lines):
    command("ftp -n 172.16.250.100 2121 <<'EOF'\nuser anonymous pcm\nbinary\n" + '\n'.join(lines) + '\nquit\nEOF')

def sha(path): return hashlib.sha256(path.read_bytes()).hexdigest()

try:
    if sys.argv[1:] == ['rollback']:
        command('mount -uw /mnt/app')
        command('test -f ' + apps + '/dio_manager.before-prompt-slider')
        command('if [ -f ' + jar_dir + '/pcm-prompt-level.jar ]; then mv ' + jar_dir + '/pcm-prompt-level.jar ' + jar_dir + '/pcm-prompt-level.jar.disabled; fi')
        command('cp -p ' + apps + '/dio_manager.before-prompt-slider ' + apps + '/dio_manager.rollback.new && mv ' + apps + '/dio_manager.rollback.new ' + apps + '/dio_manager')
        command('sync')
        print('Restored no-duck binary; slider JAR disabled. Reboot required.')
    elif sys.argv[1:] in (['install'], ['install-rotary']):
        rotary = sys.argv[1:] == ['install-rotary']
        original = base / 'P3645-dio_manager'
        candidate = base / 'sound-control' / ('pcm-prompt-rotary.jar' if rotary else 'pcm-prompt-level.jar')
        ftp(['put ' + apps + '/dio_manager.duck-stock stock-' + stamp,
             'put ' + apps + '/dio_manager current-' + stamp])
        if sha(base / ('stock-' + stamp)) != sha(original): raise RuntimeError('stock backup mismatch')
        expected = sha(base / 'P3645-dio_manager.duck-disabled')
        if sha(base / ('current-' + stamp)) != expected: raise RuntimeError('current binary differs from tested no-duck version')
        command('mount -uw /mnt/app')
        command('test ! -e ' + jar_dir + '/pcm-prompt-level.jar')
        if rotary:
            ftp(['put ' + apps + '/dio_manager.before-prompt-slider backup-check-' + stamp])
            if sha(base / ('backup-check-' + stamp)) != expected: raise RuntimeError('rollback backup mismatch')
        else:
            command('test ! -e ' + apps + '/dio_manager.before-prompt-slider')
            command('cp -p ' + apps + '/dio_manager ' + apps + '/dio_manager.before-prompt-slider')
        command('mkdir -p /mnt/ols/pcm-custom')
        command('test -w /mnt/ols/pcm-custom')
        ftp(['get sound-control/' + candidate.name + ' ' + jar_dir + '/pcm-prompt-level.jar.pending',
             'put ' + jar_dir + '/pcm-prompt-level.jar.pending jar-stage-' + stamp])
        if sha(base / ('jar-stage-' + stamp)) != sha(candidate): raise RuntimeError('JAR transfer mismatch')
        command('cp -p ' + apps + '/dio_manager.duck-stock ' + apps + '/dio_manager.slider.new')
        command('chmod 755 ' + apps + '/dio_manager.slider.new')
        command('mv ' + apps + '/dio_manager.slider.new ' + apps + '/dio_manager')
        command('chmod 644 ' + jar_dir + '/pcm-prompt-level.jar.pending')
        command('mv ' + jar_dir + '/pcm-prompt-level.jar.pending ' + jar_dir + '/pcm-prompt-level.jar')
        command('sync')
        ftp(['put ' + apps + '/dio_manager native-installed-' + stamp,
             'put ' + jar_dir + '/pcm-prompt-level.jar jar-installed-' + stamp])
        if sha(base / ('native-installed-' + stamp)) != sha(original): raise RuntimeError('installed native mismatch')
        if sha(base / ('jar-installed-' + stamp)) != sha(candidate): raise RuntimeError('installed JAR mismatch')
        print('Installed and readback verified. Backup: dio_manager.before-prompt-slider')
        print('JAR SHA256=' + sha(candidate))
        print('Reboot required; current HMI still uses its prior loaded classes.')
    else:
        raise SystemExit('Usage: deploy.py install|install-rotary|rollback')
finally:
    c.send_line('exit'); c.sock.close()
