"""Render production UI world GLSL in surfaceless Mesa EGL; no Android fidelity claim."""
import argparse
import ctypes as c
import hashlib
import json
import pathlib
import re
import time
import numpy as np
from PIL import Image
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--width', type=int, default=360)
parser.add_argument('--height', type=int, default=720)
parser.add_argument('--out', type=pathlib.Path)
args = parser.parse_args()
root = pathlib.Path(__file__).resolve().parents[2]
egl = c.CDLL('libEGL.so.1')
gl = c.CDLL('libGL.so.1')

def bind(lib, name, ret, args):
    f = getattr(lib, name)
    f.restype = ret
    f.argtypes = args
    return f
ptr = c.c_void_p
GLint = c.c_int
GLuint = c.c_uint
GLfloat = c.c_float
getproc = bind(egl, 'eglGetProcAddress', ptr, [c.c_char_p])
platform = c.CFUNCTYPE(ptr, GLuint, ptr, c.POINTER(GLint))(getproc(b'eglGetPlatformDisplayEXT'))
d = platform(12765, None, None)
a = GLint()
b = GLint()
assert bind(egl, 'eglInitialize', GLuint, [ptr, c.POINTER(GLint), c.POINTER(GLint)])(d, c.byref(a), c.byref(b))
egl.eglBindAPI(12448)
attrs = (GLint * 13)(12324, 8, 12323, 8, 12322, 8, 12321, 8, 12339, 1, 12352, 64, 12344)
config = ptr()
n = GLint()
assert bind(egl, 'eglChooseConfig', GLuint, [ptr, c.POINTER(GLint), c.POINTER(ptr), GLint, c.POINTER(GLint)])(d, attrs, c.byref(config), 1, c.byref(n))
width, height = (args.width, args.height)
assert width > 0 and height > 0
surf = bind(egl, 'eglCreatePbufferSurface', ptr, [ptr, ptr, c.POINTER(GLint)])(d, config, (GLint * 5)(12375, width, 12374, height, 12344))
ctx = bind(egl, 'eglCreateContext', ptr, [ptr, ptr, ptr, c.POINTER(GLint)])(d, config, None, (GLint * 3)(12440, 3, 12344))
assert bind(egl, 'eglMakeCurrent', GLuint, [ptr, ptr, ptr, ptr])(d, surf, surf, ctx)
getstr = bind(gl, 'glGetString', c.c_char_p, [GLuint])
print('GPU', getstr(7937), getstr(7938), flush=True)
create = bind(gl, 'glCreateShader', GLuint, [GLuint])
source = bind(gl, 'glShaderSource', None, [GLuint, GLint, c.POINTER(c.c_char_p), c.POINTER(GLint)])
compile = bind(gl, 'glCompileShader', None, [GLuint])
getshader = bind(gl, 'glGetShaderiv', None, [GLuint, GLuint, c.POINTER(GLint)])
shaderlog = bind(gl, 'glGetShaderInfoLog', None, [GLuint, GLint, c.POINTER(GLint), c.c_char_p])
shaders = []
for filename, typ in [('world_vert.glsl', 35633), ('world_frag.glsl', 35632)]:
    code = (root / 'app/src/main/assets/ui2' / filename).read_bytes()
    shader = create(typ)
    source(shader, 1, c.byref(c.c_char_p(code)), None)
    compile(shader)
    ok = GLint()
    getshader(shader, 35713, c.byref(ok))
    log = c.create_string_buffer(20000)
    shaderlog(shader, len(log), None, log)
    print(filename, 'compiled', ok.value, log.value.decode(), flush=True)
    assert ok.value
    shaders.append(shader)
program = bind(gl, 'glCreateProgram', GLuint, [])()
attach = bind(gl, 'glAttachShader', None, [GLuint, GLuint])
[attach(program, s) for s in shaders]
bind(gl, 'glLinkProgram', None, [GLuint])(program)
ok = GLint()
bind(gl, 'glGetProgramiv', None, [GLuint, GLuint, c.POINTER(GLint)])(program, 35714, c.byref(ok))
assert ok.value
bind(gl, 'glUseProgram', None, [GLuint])(program)
loc = bind(gl, 'glGetUniformLocation', GLint, [GLuint, c.c_char_p])
u1f = bind(gl, 'glUniform1f', None, [GLint, GLfloat])
u1i = bind(gl, 'glUniform1i', None, [GLint, GLint])
u2f = bind(gl, 'glUniform2f', None, [GLint, GLfloat, GLfloat])
u4f = bind(gl, 'glUniform4f', None, [GLint, GLfloat, GLfloat, GLfloat, GLfloat])
u4v = bind(gl, 'glUniform4fv', None, [GLint, GLint, c.POINTER(GLfloat)])

def uniform_location(name):
    return loc(program, name.encode())
vao = GLuint()
bind(gl, 'glGenVertexArrays', None, [GLint, c.POINTER(GLuint)])(1, c.byref(vao))
bind(gl, 'glBindVertexArray', None, [GLuint])(vao)
tex = GLuint()
bind(gl, 'glGenTextures', None, [GLint, c.POINTER(GLuint)])(1, c.byref(tex))
bind(gl, 'glBindTexture', None, [GLuint, GLuint])(3553, tex)
param = bind(gl, 'glTexParameteri', None, [GLuint, GLuint, GLint])
for pname, value in [(10241, 9729), (10240, 9729), (10242, 33071), (10243, 33071)]:
    param(3553, pname, value)
scenery_name = 'ui2_lake_dawn_landscape.png' if width > height else 'ui2_lake_dawn.png'
scenery_path = root / 'app/src/main/res/drawable-nodpi' / scenery_name
scenery_horizon = 0.443 if width > height else 0.371
im = Image.open(scenery_path).convert('RGBA')
im = im.resize((im.width // 2, im.height // 2))
raw = im.tobytes()
bind(gl, 'glTexImage2D', None, [GLuint, GLint, GLint, GLint, GLint, GLint, GLuint, GLuint, ptr])(3553, 0, 6408, im.width, im.height, 0, 6408, 5121, raw)
u1i(uniform_location('uScenery'), 0)
u1f(uniform_location('uSceneryHorizon'), scenery_horizon)
u1i(uniform_location('uLandscapeScenery'), int(width > height))
u2f(uniform_location('uResolution'), width, height)
bind(gl, 'glViewport', None, [GLint, GLint, GLint, GLint])(0, 0, width, height)
frag = (root / 'app/src/main/assets/ui2/world_frag.glsl').read_text()
declared = set(re.findall('uniform\\s+\\w+\\s+(u\\w+)', frag))
uploader = (root / 'app/src/main/java/dev/geode/ui/world/LakeWorldRenderer.kt').read_text()
uploaded = set(re.findall('shader.uniform\\("(u\\w+)', uploader))
assert declared == uploaded, (declared - uploaded, uploaded - declared)
print('Uniform audit', len(declared), 'matched', flush=True)
output = args.out or root / 'docs/ui2/validation'
output.mkdir(parents=True, exist_ok=True)
draw = bind(gl, 'glDrawArrays', None, [GLuint, GLint, GLint])
read = bind(gl, 'glReadPixels', None, [GLint, GLint, GLint, GLint, GLuint, GLuint, ptr])
finish = bind(gl, 'glFinish', None, [])
error = bind(gl, 'glGetError', GLuint, [])
report = []
anchors = (GLfloat * 20)(0.5, 0.53, 0.1, 1, 0.23, 0.35, 0.08, 0, 0.77, 0.35, 0.08, 0, 0.23, 0.71, 0.08, 0, 0.77, 0.71, 0.08, 0)
pixels = {}
for name, route, t, motion, audio, lenses, age, orbit in [('world-player-daylight', 0, 1.0, 1, (0.04, 0.06, 0.03, 0), 0, 100, 0), ('world-player-lenses', 0, 1.0, 1, (0.26, 0.32, 0.12, 0.55), 5, 0.4, 1), ('world-library', 1, 2.0, 1, (0.1, 0.1, 0.03, 0), 0, 100, 0), ('world-reduced-motion', 0, 0.0, 0, (0, 0, 0, 0), 5, 100, 1), ('world-reduced-motion-later', 0, 120.0, 0, (0, 0, 0, 0), 5, 100, 1), ('world-live-audio', 0, 1.0, 1, (0.7, 0.8, 0.45, 0.8), 0, 0.1, 0), ('world-orbit-live-audio', 0, 1.25, 1, (0.7, 0.8, 0.45, 0.8), 5, 0.1, 1)]:
    for uniform, value in [('uRoute', route), ('uTime', t), ('uMotion', motion), ('uOrbit', orbit), ('uImpulseAge', age)]:
        u1f(uniform_location(uniform), value)
    u4f(uniform_location('uAudio'), *audio)
    u1i(uniform_location('uLensCount'), lenses)
    u4v(uniform_location('uLenses[0]'), 5, anchors)
    start = time.monotonic()
    draw(4, 0, 3)
    finish()
    buf = (c.c_ubyte * (width * height * 4))()
    read(0, 0, width, height, 6408, 5121, buf)
    elapsed = time.monotonic() - start
    arr = np.ctypeslib.as_array(buf).reshape(height, width, 4)[::-1]
    Image.fromarray(arr).save(output / (name + '.png'))
    pixels[name] = arr.copy()
    rgb = arr[:, :, :3] / 255
    luma = rgb @ np.array([0.2126, 0.7152, 0.0722])
    entry = {'capture': name, 'mean_luma': float(luma.mean()), 'fraction_black': float((luma < 0.02).mean()), 'gl_error': error(), 'software_render_seconds': elapsed}
    assert entry['gl_error'] == 0
    assert entry['fraction_black'] < 0.15
    report.append(entry)
    print(entry, flush=True)
assert np.array_equal(pixels['world-reduced-motion'], pixels['world-reduced-motion-later']), 'Reduced motion changed with time'
audio_delta = float(np.abs(pixels['world-live-audio'].astype(float) - pixels['world-player-daylight'].astype(float)).mean())
assert audio_delta > 0.05, 'Live audio produced no visible response'
report_path = output / 'world-gles-report.json'
results = {
    'renderer': getstr(7937).decode(),
    'gles': getstr(7938).decode(),
    'uniform_audit': 'all declared uniforms uploaded',
    'shader_sha256': hashlib.sha256((root / 'app/src/main/assets/ui2/world_frag.glsl').read_bytes()).hexdigest(),
    'scenery_file': scenery_name,
    'scenery_horizon': scenery_horizon,
    'scenery_sha256': hashlib.sha256(scenery_path.read_bytes()).hexdigest(),
    'reduced_motion_time_invariance': True,
    'live_audio_mean_channel_delta': audio_delta,
    'dimensions': [width, height],
    'captures': report,
    'limits': (
        'Actual production GLSL rendered with Mesa llvmpipe. Does not validate Android '
        'lifecycle, SurfaceView composition, phone shader drivers, memory, thermal '
        'behavior, sustained frame rate or accessibility.'
    ),
}
report_path.write_text(json.dumps(results, indent=2) + '\n')
print(report_path, flush=True)
