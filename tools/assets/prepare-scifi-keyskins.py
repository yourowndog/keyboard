#!/usr/bin/env python3
"""Prepare key-skin packs from Sam's installed Sci-Fi Keyboard APK, for personal builds only.

    python3 -I tools/assets/prepare-scifi-keyskins.py [APK_DIR] [OUT_DIR]

APK_DIR holds base.apk (+ split_config.xxhdpi.apk) pulled from icarion; default
~/omniboard-study/scifi-keyboard/apk on sleeper. OUT_DIR defaults to the asset pack's
extracted/scifi-keyboard/prepared/keyskins, which private-picks.txt stages into the gitignored
private-assets/keyskins/. Nothing this writes may be committed (docs/theming/lcars-assets.md).

The app picks each theme's slot textures from int arrays in classes.dex, and their resource numbers
are not aligned across slots (theme 1's delete key is btn_back11, its arrows arrow_1..4). The table
below is those arrays, decoded on 2026-11-12. Each texture is copied byte for byte: the compiled
nine-patch chunk (npTc) stays in the PNG, and Android decodes it from assets at runtime. The file
name records the source density, e.g. enter@640.9.png, so the keyboard can scale it correctly.
"""
import zipfile, re, os, shutil, sys
apkdir = os.path.expanduser(sys.argv[1] if len(sys.argv) > 1 else '~/omniboard-study/scifi-keyboard/apk')
out = os.path.expanduser(sys.argv[2] if len(sys.argv) > 2 else
                         '~/documents/lcars-trek-assets/extracted/scifi-keyboard/prepared/keyskins')
dens = {'mdpi':160,'hdpi':240,'xhdpi':320,'xxhdpi':480,'xxxhdpi':640}
key_un = ['key_unpress_%d' % n for n in range(11, 26)] + ['key_unpress_25'] * 5
key_pr = ['key_press_%d' % n for n in range(11, 26)] + ['key_press_%d' % n for n in (27, 28, 29, 30, 24)]
def slots(t):  # t = 1..20
    i = t - 1
    a = lambda first, stem: first if t == 1 else '%s%d' % (stem, t)
    return {
        'key': key_un[i], 'key_pressed': key_pr[i],
        'space': 'btn_space%d' % (11 + i), 'enter': 'btn_enter%d' % (11 + i),
        'delete': 'btn_back%d' % (11 + i),
        'shift': ('btn_shift_off11' if t == 1 else 'btn_shift_off_%d' % (11 + i)),
        'shift_on': 'btn_shift_on_%d' % (11 + i),
        'arrow_left': a('arrow_2', 'arrow_left'), 'arrow_right': a('arrow_1', 'arrow_right'),
        'arrow_up': a('arrow_4', 'arrow_up'), 'arrow_down': a('arrow_3', 'arrow_down'),
        'popup': 'popup_%02d' % t,
    }
files = {}
for apk in ['base.apk', 'split_config.xxhdpi.apk']:
    if not os.path.isfile(os.path.join(apkdir, apk)):
        continue
    z = zipfile.ZipFile(os.path.join(apkdir, apk))
    for n in z.namelist():
        m = re.match(r'res/drawable-([a-z]+dpi)(?:-v\d+)?/([^/]+?)(\.9)?\.png$', n)
        if not m or m.group(1) not in dens: continue
        d = dens[m.group(1)]
        name = m.group(2)
        if name not in files or files[name][0] < d:
            files[name] = (d, apk, n, bool(m.group(3)))
if os.path.isdir(out): shutil.rmtree(out)
missing = []
for t in range(1, 21):
    for slot, res in slots(t).items():
        if res not in files: missing.append((t, slot, res)); continue
        d, apk, n, nine = files[res]
        dst = os.path.join(out, 'sfk-%02d' % t, '%s@%d%s.png' % (slot, d, '.9' if nine else ''))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with zipfile.ZipFile(os.path.join(apkdir, apk)) as z: open(dst, 'wb').write(z.read(n))
for name, (d, apk, n, nine) in files.items():
    m = re.fullmatch(r'custom_key_(\d+)', name)
    if m:
        dst = os.path.join(out, 'swatches', 'swatch-%03d@%d.png' % (int(m.group(1)), d))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with zipfile.ZipFile(os.path.join(apkdir, apk)) as z: open(dst, 'wb').write(z.read(n))
print('missing', missing)
for t in sorted(os.listdir(out)): print(t, len(os.listdir(os.path.join(out, t))))
