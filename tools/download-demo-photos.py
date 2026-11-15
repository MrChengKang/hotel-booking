"""Download the small, explicit stock-photo bank before any database import."""
import concurrent.futures, json, pathlib, urllib.request

root = pathlib.Path(__file__).resolve().parent.parent
# Installed under backend/tools; accept an explicit cache directory for staging.
import argparse
parser = argparse.ArgumentParser()
parser.add_argument('--directory', default=str(root / '.demo-assets'))
options = parser.parse_args()
manifest = json.loads(pathlib.Path(__file__).with_name('demo-photo-sources.json').read_text(encoding='utf-8'))
directory = pathlib.Path(options.directory).resolve()
directory.mkdir(parents=True, exist_ok=True)

def download(photo):
    target = directory / (photo['id'] + '.jpg')
    if target.is_file() and target.read_bytes().startswith(b'\xff\xd8'):
        return
    url = 'https://images.unsplash.com/' + photo['id'] + '?auto=format&fit=crop&w=1400&q=82&fm=jpg'
    with urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'STAY-Portfolio-Demo/1.0'}), timeout=40) as response:
        data = response.read(5 * 1024 * 1024 + 1)
    if len(data) > 5 * 1024 * 1024 or not data.startswith(b'\xff\xd8'):
        raise RuntimeError('Expected a JPG smaller than 5 MB: ' + photo['id'])
    target.write_bytes(data)

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    list(pool.map(download, manifest['photos']))
print('Verified', len(manifest['photos']), 'stock JPG files. License/source manifest is included.')
