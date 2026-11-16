import os, sys, time, re
from pathlib import Path
sys.stdout.reconfigure(encoding='utf-8', errors='replace')

# Reuse only the transport definitions, never the installation commands.
source = Path(__file__).with_name('pcm_transport.py').read_text()
exec(source.split('client = MinimalTelnet(HOST, PORT)')[0])
c = MinimalTelnet(HOST, PORT)
c.read_until(b'login:'); c.send_line(USER)
c.read_until(b'Password:'); c.send_line(PASSWORD)
c.read_until(b'#')
c.send_line('stty -echo')
time.sleep(0.2)
commands = sys.argv[1:]
if commands == ['download-hmi']:
    commands = ["ftp -n 172.16.250.100 2121 <<'EOF'\nuser anonymous pcm\nbinary\nput /ifs/lsd.jxe P3645-lsd.jxe\nquit\nEOF"]
elif commands == ['download-audiotable']:
    commands = ["ftp -n 172.16.250.100 2121 <<'EOF'\nuser anonymous pcm\nbinary\nput /net/rcc/etc/MIB_AudioTable_PO_G24.xml P3645-MIB_AudioTable_PO_G24.xml\nquit\nEOF"]
elif commands == ['download-audioprocess']:
    commands = ["ftp -n 172.16.250.100 2121 <<'EOF'\nuser anonymous pcm\nbinary\nput /net/rcc/bin/AudioProcess P3645-AudioProcess\nquit\nEOF"]
for command in commands:
    token = 'DONE' + str(time.time_ns())
    c.send_line(command + '\necho ' + token)
    output = c.read_until(b'\n' + token.encode(), 60).decode('utf-8', 'replace')
    print(output.replace('\x00', ''))
c.send_line('exit'); c.sock.close()
