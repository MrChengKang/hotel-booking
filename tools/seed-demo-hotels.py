"""Explicit STAY demo import. Run from the backend; secrets remain in .env.local."""
import argparse, json, os, pathlib, shutil, subprocess, sys, zipfile

parser = argparse.ArgumentParser()
mode = parser.add_mutually_exclusive_group()
mode.add_argument('--apply-demo', action='store_true')
mode.add_argument('--verify-h2', action='store_true')
parser.add_argument('--assets')
options = parser.parse_args()
backend = pathlib.Path(__file__).resolve().parent.parent
work = backend / '.tools' / 'demo-seed'
work.mkdir(parents=True, exist_ok=True)
sources = pathlib.Path(__file__).with_name('SeedDemoHotels.java')
jars = sorted((backend / 'target').glob('*.jar'))
if not jars:
    sys.exit('Build the backend first with mvnw package -DskipTests.')
java_home = pathlib.Path(os.environ.get('JAVA_HOME', 'C:/Program Files/Java/jdk-23'))
java = java_home / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
javac = java_home / 'bin' / ('javac.exe' if os.name == 'nt' else 'javac')
if not java.is_file() or not javac.is_file():
    sys.exit('Set JAVA_HOME to a JDK 21+ installation before running this tool.')

dependencies = []
with zipfile.ZipFile(jars[-1]) as archive:
    for item in archive.infolist():
        if item.filename.startswith('BOOT-INF/lib/') and item.filename.count('/') == 2 and item.filename.endswith('.jar'):
            target = work / 'lib' / pathlib.PurePosixPath(item.filename).name
            target.parent.mkdir(exist_ok=True)
            if not target.exists() or target.stat().st_size != item.file_size:
                target.write_bytes(archive.read(item))
            dependencies.append(target)
if not dependencies:
    sys.exit('Expected a packaged Spring Boot jar with BOOT-INF/lib dependencies.')
if options.verify_h2:
    h2 = sorted((pathlib.Path.home() / '.m2/repository/com/h2database/h2').glob('*/h2-*.jar'))
    if not h2: sys.exit('Run the backend tests once to download the isolated H2 test driver.')
    dependencies.append(h2[-1])
classes = work / 'classes'
classes.mkdir(exist_ok=True)
classpath = os.pathsep.join(str(p) for p in [backend / 'target/classes', *dependencies])

def argfile(name, args):
    path = work / name
    path.write_text('\n'.join(json.dumps(str(value).replace('\\', '/'), ensure_ascii=False) for value in args), encoding='utf-8')
    return '@' + str(path)

logpath = work / 'private.log'
with logpath.open('w', encoding='utf-8') as log:
    compile_args = argfile('compile.args', ['--release','21','-classpath',classpath,'-d',classes,sources])
    compile_result = subprocess.run([str(javac), compile_args], cwd=backend, stdout=log, stderr=log)
    if compile_result.returncode:
        sys.exit('Tool compilation failed. See .tools/demo-seed/private.log in the backend.')
    args = ['-Xms64m','-Xmx512m','-cp',str(classes)+os.pathsep+classpath,'com.hotel.hotel_booking.tools.SeedDemoHotels']
    args += ['--verify-h2'] if options.verify_h2 else ['--apply-demo'] if options.apply_demo else ['--plan']
    if options.assets: args += ['--assets='+str(pathlib.Path(options.assets).resolve())]
    process = subprocess.Popen([str(java), argfile('run.args', args)], cwd=backend, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, encoding='utf-8', errors='replace')
    for line in process.stdout:
        log.write(line)
        log.flush()
        if line.startswith('DEMO_'): print(line.rstrip(), flush=True)
    code = process.wait()
if code: print('Demo import failed. See backend/.tools/demo-seed/private.log; no credentials are printed here.', flush=True)
sys.exit(code)
