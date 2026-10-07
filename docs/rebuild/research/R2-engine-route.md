# R2: 3D engine route for D08 (Diligent Engine prototype vs extending our GLES engine)

Research date 2026-10-07. Read-only: nothing was compiled, tested or linted. Code was read on
`origin/codex/account-visual-foundation` (PR #9) with `git show`; blueprint docs on `origin/main`.
Graphify (repository `tessie1993/geode-3`) was used first. Its index is at commit `fd467d1`, built
2026-10-07T01:07Z, and it does not contain `SpatialCameraDirector` (0 matches). It found `SceneRegistry`,
`MilkdropScene`, `EncoderSurface` and the wallpaper GL view. Everything else was read from the PR #9 branch.

Marking convention: **[V]** = read in a primary source this session (URL given). **[WF]** = obtained through
the WebFetch summariser, so treat exact hashes and dates as "verify with `git ls-remote`". **[U]** = unverified
inference or estimate; the prototype must confirm it. **[P]** = a proposal of mine, not a fact.

---

## A. Recommendation and reasons

**Run D08 as a time-boxed, GLES-only Diligent prototype, behind a CMake option that is OFF by default.**
- Route A: attach to the already-current EGL context.
- Diligent's GL backend is packaged as its own shared library.
- Vulkan and AHardwareBuffer interop are not started.
- Pre-register the no-go rules in section C10.

**My forecast:** the attach will work. A full Diligent + DiligentFX stack is unlikely to beat extending our own
GLES engine on size, CI time and risk. So design D09 (camera director, post-stack contract, scene contract,
tier budgets) to be engine-neutral. Then a no-go costs nothing and the fallback in section D is already planned.

Top reasons:
1. **Attach is real, but it is a loan of the context, not isolation.**
   - `IEngineFactoryOpenGL::AttachToActiveGLContext` exists and creates no swap chain. The app presents. [V]
   - On Android, `GLContext::Init` reuses the current EGL surface and context when they exist. [V]
   - Diligent never reads or restores the app's GL state. `GLContextState::Invalidate()` actively unbinds program, pipeline,
     VAO and both FBOs. Its Android stub file defines global GL function-pointer variables named like the real GLES
     functions (`glDispatchCompute`, ...). [V]
   - So it needs a state fence (we already have `resetFrameState()` at `core/viz/Quad.cpp:36`) and it must live in
     its own `.so`.
2. **DiligentFX does not need compute, but it is a heavy, untested-on-Android dependency chain.**
   - Bloom, DOF, TAA, SSR and SSAO are all full-screen pixel-shader passes, with zero compute pipelines. [V]
   - Their shaders are HLSL. On GLES they need the runtime HLSL-to-GLSL converter, so `DILIGENT_NO_HLSL` is not an option for them. [V]
   - DiligentFX hard-links DiligentTools (libpng, libjpeg, libtiff, zlib, Dear ImGui, GLTF loader) and fetches
     EnTT with FetchContent at configure time. [V]
   - DiligentFX's README platform table has no Android row. DiligentCore's Android CI builds only armeabi-v7a and
     arm64-v8a, with NDK 27 and CMake 3.22.1. We use NDK r30, CMake 4.1.2 and x86_64. [V]
3. **Our GLES engine already has the hard parts, so the fallback is incremental.**
   - ES 3.1 compute tiering and capability probing: `GlCaps.hpp:63-95`.
   - Probed float formats and MRT limit: `GlCaps.hpp:19,44,99-115`.
   - Program-binary cache, trails, bloom shaders, composite, safety clamp, thermal governor, and context recovery in `Renderer::onSurfaceCreated`.
   - The "look ceiling" is set by shaders, camera, particles and post chain, not by the engine library.
   - Diligent's main extra value (portable backends, Vulkan) is explicitly outside the critical path in
     `docs/blueprint/OPEN_SOURCE.md`.

Route comparison ([U] unless noted; effort in engineer-days is my estimate [P]):

| | A: Diligent, attach in our context | B: Diligent in a sibling shared EGL context | V: Diligent Vulkan + AHardwareBuffer to GLES | G: extend our GLES engine |
|---|---|---|---|---|
| Prototype effort | 8-12 d | +3-4 d on top of A | 20-30 d | 18-25 d for the whole D09 post stack, no separate prototype |
| projectM isolation | state fence; shared state machine | strong (separate GL state), plus 2 `eglMakeCurrent` per frame | strong (separate API) | none needed |
| Look ceiling | same shaders, same ceiling; FX gives ready bloom/DOF/TAA | same | same, plus compute on ES 3.0 devices with Vulkan | same; compute only on ES 3.1+ devices (already tiered) |
| Export | works: the encoder surface is already current | works | AHB round trip, fence sync; needs a GL composite anyway | works unchanged |
| Size/CI | highest | highest | highest (adds Vulkan backend, volk, glslang/SPIRV-Cross unless offline) | zero |
| Main risk | state leaks, symbol clash, API churn | cross-context sync cost | driver matrix, sync | schedule |

---

## B. Evidence per question

### B1. Diligent today

**Version and licence**
- Newest tag on DiligentEngine: `API256019`, 2026-06-19, commit `138d8ec` ("Add SHADER_OPTIMIZATION_LEVEL to
  ShaderCreateInfo"). Previous tags: API256018 (2026-06-19), API256015 (2026-03-26), API256014 (2026-03-05),
  API256013 (2026-01-29), API256011 (2025-08-31). [WF] https://github.com/DiligentGraphics/DiligentEngine/tags
- Full SHA given by WebFetch: `138d8ec2c5ffd522d7cecce4871f3a0e75ed0b85`. [WF] https://github.com/DiligentGraphics/DiligentEngine/commit/138d8ec
- Master HEAD on 2026-10-05 is `efe5da2`, with Vulkan SDK 1.4.363 and super-resolution changes. [WF]
  https://github.com/DiligentGraphics/DiligentEngine/commits/master
- The GitHub Releases page still lists `v2.5.6` (2024-09-02) as the newest release. Newer work is only tagged
  `API2560xx`. [WF] https://github.com/DiligentGraphics/DiligentEngine/releases. Pin a tag, not master.
- That tag pins these submodules [WF] https://github.com/DiligentGraphics/DiligentEngine/tree/API256019:
  - DiligentCore `0d88143` (matches the DiligentCore tag listing, https://github.com/DiligentGraphics/DiligentCore/tags)
  - DiligentFX `4b3c2bf`
  - DiligentTools `c82be22`
  - DiligentSamples `4a496c8`
- Licence is Apache-2.0. [V] https://raw.githubusercontent.com/DiligentGraphics/DiligentEngine/API256019/README.md and
  `docs/blueprint/OPEN_SOURCE.md` (License.txt blob `d9a10c0d8e868ebf8da0b3dc95bb0be634c34bfe`).
- Third-party licences listed by Core: [V] https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/README.md
  - Vulkan-Headers, SPIRV-Cross, SPIRV-Tools: Apache-2.0
  - SPIRV-Headers: Khronos MIT-like
  - glslang: BSD-3, BSD-2, MIT, Apache-2.0
  - glew: Mesa/Khronos MIT-like
  - volk: MIT-like
  - stb: MIT or public domain
  - googletest: BSD-3
  - DirectXShaderCompiler: LLVM licence
  - xxHash: BSD-2
- DiligentTools adds libjpeg-9e, libtiff, libpng, zlib, imgui, args, json (submodules in `.gitmodules`). [V]
  https://raw.githubusercontent.com/DiligentGraphics/DiligentTools/API256019/.gitmodules. All need entries in
  `THIRD_PARTY_NOTICES`. EnTT's MIT licence is [U] (not opened).

**Android status and minimum APIs**
- Platform table: Android = OpenGL/GLES and Vulkan. Minimums: OpenGLES 3.0, Vulkan 1.0. [V]
  https://raw.githubusercontent.com/DiligentGraphics/DiligentEngine/API256019/README.md lines 25 and 74-81
- README says NDK r24 or later. [V] same file, lines 315-319
- Upstream Android CI [V]:
  - Workflow: https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/.github/workflows/build-android.yml
  - Matrix: `armeabi-v7a`, `arm64-v8a`. No x86_64.
  - Build: `./gradlew buildCMakeDebug`.
  - Gradle project: https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/BuildTools/Android/tests/build.gradle
    - NDK `27.0.12077973`
    - CMake `3.22.1`
    - minSdk 28
    - `-DANDROID_STL=c++_static`
    - AGP 8.5.0 (`BuildTools/Android/build.gradle`)
    - Gradle 8.7 (`gradle/wrapper/gradle-wrapper.properties`)
- DiligentFX README platform table lists Windows, UWP, Linux, macOS, iOS, tvOS and Web. No Android. [V]
  https://raw.githubusercontent.com/DiligentGraphics/DiligentFX/API256019/README.md
- GL backend feature gating on GLES [V] https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/Graphics/GraphicsEngineOpenGL/src/RenderDeviceGLImpl.cpp lines 945-1014:
  - Compute shaders: ES 3.1 or `compute_shader`.
  - Separable programs: ES 3.1 or `separate_shader_objects`.
  - Texture views: ES 3.1 or `texture_view`.
  - Independent blend, geometry shaders, tessellation: ES 3.2.
  - Multisample textures: ES 3.1.
  - LOD bias: ES 3.1.
- Old reports of crashes on ES 3.0-only devices: issue #58 (2019, closed, resolution not shown to the fetcher) [WF]
  https://github.com/DiligentGraphics/DiligentCore/issues/58. Issue #61, precision qualifiers on shadow samplers
  crashed some old Android devices; the fix applies the qualifiers only on ES 3.1+ [WF-search snippet]
  https://github.com/DiligentGraphics/DiligentCore/issues/61.
- **Adreno/Mali:** I found no current Adreno- or Mali-specific Diligent issue in two searches. That is absence of
  evidence, not evidence of absence. The owner device matrix in C9 must include one Adreno and one Mali.

**Builds with NDK r30 + CMake 4.x? Not verified [U].** Facts that bear on it:
- All CMake minimum versions I read are at or above 3.10, so CMake 4.x should only warn, not error. (CMake 4.0 removed
  compatibility below 3.5; I did not re-fetch the CMake docs this session.)

| Project | Minimum | Source |
|---|---|---|
| DiligentCore | 3.19 | https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/CMakeLists.txt |
| DiligentCore/ThirdParty | 3.13 | `ThirdParty/CMakeLists.txt` |
| DiligentTools | 3.10 | https://raw.githubusercontent.com/DiligentGraphics/DiligentTools/API256019/CMakeLists.txt |
| DiligentFX | 3.10 | `CMakeLists.txt` at `API256019`, https://raw.githubusercontent.com/DiligentGraphics/DiligentFX/API256019/CMakeLists.txt |
| glslang, SPIRV-Tools | 3.22.1 | https://raw.githubusercontent.com/DiligentGraphics/glslang/master/CMakeLists.txt |
| SPIRV-Cross | 3.10 | https://raw.githubusercontent.com/DiligentGraphics/SPIRV-Cross/master/CMakeLists.txt |
| volk | 3.5...3.30 | https://raw.githubusercontent.com/DiligentGraphics/volk/master/CMakeLists.txt |
| xxHash | 3.10 | https://raw.githubusercontent.com/DiligentGraphics/xxHash/dev/build/cmake/CMakeLists.txt |

  The third-party rows are the forks' default branches, not the pinned SHAs. The glslang, SPIRV-Tools, SPIRV-Cross and volk
  rows only matter if the SPIR-V toolchain or Vulkan are enabled.
- Therefore `CMAKE_POLICY_VERSION_MINIMUM` is probably **not** needed for the minimal GLES profile. Add
  `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` as insurance in the prototype wrapper and record whether it was needed. [U]
- The Diligent CMake sets `-mavx2` for Clang Release builds when `TARGET_CPU` is `x86_64`
  (core CMakeLists.txt ~lines 477-483). [V] That would be baked into our x86_64 `libGraphicsEngineOpenGL.so`. A device
  or emulator without AVX2 would die with SIGILL. Check with `llvm-objdump -d ... | grep -c ymm` and override.
- DiligentCore sets no `CMAKE_CXX_STANDARD` (grep of the root file). [V] Our root sets C++20
  (`CMakeLists.txt:4-6`), which Diligent would inherit. Isolate it (see C2).
- NDK r30's Clang is newer than what upstream CI used. New diagnostics are only warnings (no `-Werror` in
  `Diligent-BuildSettings`, core CMakeLists.txt ~lines 489-500). [V] Library-removal breakage is possible. [U]
- minSdk 26 vs upstream CI 28. Unresolved symbols above API 26 would fail at link time, which makes this check cheap. [U]

**Submodules (what is actually required)**
- GLES-only, no Vulkan/WebGPU/Metal, no archiver, no DXC: **DiligentCore only.**
  - Its needed third party is `ThirdParty/xxHash` (always) and in-tree `stb`.
  - glslang, SPIRV-Tools, SPIRV-Cross and SPIRV-Headers are built only when Vulkan, Metal, WebGPU, or
    (Archiver and GL/GLES) is enabled. [V] `ThirdParty/CMakeLists.txt` line 35:
    `if (VULKAN_SUPPORTED OR METAL_SUPPORTED OR WEBGPU_SUPPORTED OR (ARCHIVER_SUPPORTED AND (GL_SUPPORTED OR GLES_SUPPORTED)))`
  - With `DILIGENT_NO_GLSLANG=ON` and `DILIGENT_NO_HLSL=ON`, SPIRV-Tools is skipped (line 50).
  - volk is only for Vulkan; googletest only for tests.
- Pinned SHAs of Core's third-party submodules [WF] https://github.com/DiligentGraphics/DiligentCore/tree/API256019/ThirdParty:
  glslang `b5782e5`, SPIRV-Cross `fb0c1a3`, SPIRV-Tools `262bdab`, SPIRV-Headers `b824a46`, volk `4f3bcee`,
  Vulkan-Headers `2fa2034`, xxHash `6697932`, googletest `8508785`.
  Vendored in-tree (no submodule): DirectXShaderCompiler, FSR/shaders, GPUOpenShaderUtils, OpenXR-SDK, abseil-cpp, dawn, glew, stb.
  The clone is therefore heavy.
- With DiligentFX: add DiligentTools (the FX CMake aborts without `DILIGENT_TOOLS_FOUND`). FX also runs
  `FetchContent` for EnTT v3.16.0 at configure time. [V] DiligentFX `CMakeLists.txt` lines 5-23.
  Pre-seed it with `-DFETCHCONTENT_SOURCE_DIR_ENTT=<pinned checkout>` plus
  `FETCHCONTENT_FULLY_DISCONNECTED=ON` for offline or CI builds.

**Consumption from our root CMake**
- Upstream documents both `add_subdirectory(DiligentCore)` and FetchContent (SOURCE_DIR must equal the module name). [V]
  https://raw.githubusercontent.com/DiligentGraphics/DiligentEngine/API256019/README.md ("Your Project Uses Cmake")
- **Recommend git submodules**, matching repo practice (`.gitmodules` has projectm, kissfft, taglib, oboe, plus
  `docs/visualizer-v2/provenance.json`). That gives an exact SHA pin, no configure-time network, and one provenance entry.
  Lay them out as siblings: `third_party/diligent/{DiligentCore,DiligentTools,DiligentFX}`.
- Targets to link:
  - `Diligent-GraphicsEngineOpenGL-shared` (produces `libGraphicsEngineOpenGL.so`).
  - `DiligentFX` (only in the FX profile).
  - `Diligent-BuildSettings` is for Diligent's own targets; do not link it into our code.
  - README example links `Diligent-GraphicsEngineOpenGL-shared` plus `DiligentFX`.

**Option switches** [V] (names from the Core CMakeLists.txt option list and the Engine README table lines 710-731)

| Disable | Option |
|---|---|
| D3D11/12, Metal, Vulkan, WebGPU | `DILIGENT_NO_DIRECT3D11/12`, `DILIGENT_NO_METAL`, `DILIGENT_NO_VULKAN`, `DILIGENT_NO_WEBGPU` (WebGPU is already off outside Web) |
| Archiver, packager | `DILIGENT_NO_ARCHIVER`, `DILIGENT_NO_RENDER_STATE_PACKAGER` |
| Super resolution (FSR/DLSS wrappers) | `DILIGENT_NO_SUPER_RESOLUTION` |
| glslang + SPIRV-Tools | `DILIGENT_NO_GLSLANG` |
| HLSL support in non-D3D backends (drops the HLSL-to-GLSL converter; README says this "significantly reduces" Vulkan/GL binary size) | `DILIGENT_NO_HLSL` |
| Tests, format validation | `DILIGENT_BUILD_TESTS=OFF`, `DILIGENT_NO_FORMAT_VALIDATION=ON` |
| Install rules | `DILIGENT_INSTALL_CORE=OFF`, `DILIGENT_INSTALL_FX` is already OFF on Android |
| Radient (new FX path-tracer-like component, built by default) | `DILIGENT_NO_RADIENT=ON` |
| Tools extras | `DILIGENT_NO_RENDER_STATE_PACKAGER`, `DILIGENT_ENABLE_DRACO=OFF`, `DILIGENT_USE_RAPIDJSON=OFF`; Tools' libjpeg/libtiff/libpng/zlib/imgui cannot be disabled while FX links `Diligent-AssetLoader`, `Diligent-TextureLoader` and `Diligent-Imgui` |

### B2. GLES attach

**Can it attach to the context projectM uses? Yes, in-thread.**
- `IEngineFactoryOpenGL::AttachToActiveGLContext(const EngineGLCreateInfo&, IRenderDevice**, IDeviceContext**)`.
  "The application is responsible for presenting the main frame buffer." Maximum one immediate context, no deferred
  contexts. [V] https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/Graphics/GraphicsEngineOpenGL/interface/EngineFactoryOpenGL.h lines 100-110;
  implementation `src/EngineFactoryOpenGL.cpp` lines 301-377.
- Android path: `GLContext::Init(window)` calls `InitEGLDisplay`, then uses `eglGetCurrentSurface(EGL_DRAW)` if non-null
  (`AttachToCurrentEGLSurface`), otherwise `InitEGLSurface`, which throws if no window pointer was given. It then uses the
  current EGL context if there is one (`AttachToCurrentEGLContext`, reading `GL_MAJOR_VERSION`/`GL_MINOR_VERSION`),
  otherwise creates one (tries 3.2, 3.1, 3.0). It finishes with `InitGLES()`. [V] `src/GLContextAndroid.cpp` lines 233-296.
  - GLSurfaceView thread: a surface and context are current, so no window pointer needed.
  - Export: the encoder surface is current (`EncoderSurface.kt:66-68`), so also fine.
- `InitGLES()` calls `LoadGLFunctions()` (eglGetProcAddress) and `glEnable(GL_PRIMITIVE_RESTART_FIXED_INDEX)`. [V]
  `GLContextAndroid.cpp` lines 248-260. That is a persistent state change in our context.
- README documents the attach snippet. [V] https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/Graphics/GraphicsEngineOpenGL/readme.md

**How to render into our FBO/texture**
- Preferred: wrap a GL texture we own with `IRenderDeviceGL::CreateTextureFromGLHandle(handle, bindTarget, TexDesc,
  state, &tex)`. Diligent does not take ownership and creates its own FBO for it (FBO/VAO caches keyed by GL context). [V]
  `interface/RenderDeviceGL.h`. Use `fboA_.tex()` (`Framebuffer.hpp:17`) or a new HDR texture. Our own FBO objects are untouched.
  - Diligent only learns width/height/format automatically; the rest of `TextureDesc` is ours.
  - Wrappers must be recreated when our texture is recreated (GL names are reused after delete).
- Alternative [U, more invasive]: implement `ISwapChainGL::GetDefaultFBO()` so a dummy texture maps to our `targetFbo`
  (`CreateDummyTexture`, `IDeviceContextGL::SetSwapChain`). Not recommended.
- Diligent never presents or swaps in the attach path. Our `Renderer::render(time, targetFbo)` and the encoder
  `swapBuffers` remain the only presenters.

**State Diligent assumes or clobbers, and the restore for projectM**

Observed in source [V]:
- `GLContextState::Invalidate()` runs `glUseProgram(0)`, `glBindProgramPipeline(0)`, `glBindVertexArray(0)`, and unbinds both
  `GL_DRAW_FRAMEBUFFER` and `GL_READ_FRAMEBUFFER`, then marks its caches unknown (textures, samplers, images, UBOs, SSBOs,
  depth/stencil/raster state, colour masks, active texture). It does **not** read the app's GL state.
  `src/GLContextState.cpp` lines 80-122.
- `IDeviceContext::InvalidateState()` calls it. "This method should be called by an application to invalidate internal
  cached states." `DeviceContextGL::InvalidateState` is at `DeviceContextGLImpl.cpp` lines 211-222;
  doc text in `Graphics/GraphicsEngine/interface/DeviceContext.h` ~line 2760.
- It leaves in our context after it runs: sampler objects bound on its units (`glBindSampler`, `GLContextState.cpp:254-262`),
  enabled/disabled depth, stencil, cull, blend, scissor, polygon-offset state, viewport, scissor box, colour masks, bound
  textures, UBOs and SSBOs, and `GL_PRIMITIVE_RESTART_FIXED_INDEX`.
- `MilkdropScene` documents projectM's side: "projectM assumes default GL state and restores little"
  (`MilkdropScene.cpp:215`). It brackets projectM with `resetFrameState()` (`:216`, `:237`) and re-binds the previous FBO and
  viewport (`:238-239`).
- `resetFrameState()` (`core/viz/Quad.cpp:36-52`) disables scissor, stencil, depth, cull, polygon offset and
  alpha-to-coverage, resets colour/depth/stencil masks, blend equation and unpack alignment, unbinds samplers 0..7 and the pixel
  pack/unpack buffers, and sets texture unit 0. It does **not** reset blend enable/func, viewport, program, VAO, textures,
  UBO/SSBO, or primitive restart, so the Diligent fence adds those (C6).

**Context loss and surface replacement**
- Our single recovery point is `Renderer::onSurfaceCreated()` (`Renderer.cpp:370-434`). It calls `releaseScenes()`
  (`:375`, `:529-535`), drops FBOs and textures, and lazily rebuilds scenes via `sceneFor()` (`:487-494`) and
  `buildScene()` (`:496-527`). Diligent's device must be torn down and recreated at the same point.
- Diligent has no "rebind this device to a new context" operation. A different EGL context means a new device. In the
  attach path its caches are keyed by the native context (`GLContextState::m_CurrentGLContext`,
  `GetFBOCache(m_ContextState.GetCurrentGLContext())`). [V] `UpdateCurrentGLContext()` and
  `PurgeCurrentGLContextCaches()` exist for the multi-context case. [V] `DeviceContextGLImpl.cpp` lines 1749-1764.
- **Hazard:** if the old Diligent objects are released while a *different* GL context is current (typical in
  `onSurfaceCreated` after a lost context), their `glDelete*` calls hit the wrong context and could delete unrelated
  objects with the same names. [U, follows from the destructor design]
  - Mitigation 1: record `eglGetCurrentContext()` at device creation. If it differs at teardown, do not release through
    Diligent; leak (abandon) the old device wrapper rather than deleting by name.
  - Mitigation 2: release before the old context is lost, while it is still current.
  - The existing detach path already runs contextless (`VisualizerView.kt:80-86`: GL calls are no-ops and libEGL logs one error). That case is safe.
- Surface-only replacement (rotation, fold) with `preserveEGLContextOnPause = true` (`VisualizerView.kt:50`) keeps the
  context; `GLSurfaceView` then calls only `onSurfaceChanged`. Diligent state stays valid because it never uses the default framebuffer. [U]
- The wallpaper GL view does **not** set `preserveEGLContextOnPause` (`VisualizerWallpaperService.kt:87-94`), so every
  show/hide rebuilds the context and would rebuild Diligent, including HLSL-to-GLSL conversion plus driver compile.
  Set it (best effort; drivers may refuse) or measure the hitch.

**Option B: sibling shared EGL context [U, designed from EGL semantics]**
- In `Renderer::onSurfaceCreated`, read `eglGetCurrentContext()/Display/Config` (`EGL_CONFIG_ID` query), create a second
  context with `share_context = current` plus a 1x1 pbuffer, as `core/viz/GlProfile.cpp:30-79` already does for probing.
- Diligent attaches inside the sibling. The scene renders into a *shared* GL texture. The app context samples it.
- This isolates GL state completely. It costs two `eglMakeCurrent` per frame. EGL makes `eglMakeCurrent` flush the
  previously current context [U, EGL spec, not fetched]. FBO, VAO and program objects are not shareable but textures and
  buffers are.
- Teardown becomes explicit: make the sibling current, release Diligent objects, destroy the sibling.
- Build the prototype facade so A and B differ only in a small "context scope" strategy; fall back to B if G5 fails.

### B3. Vulkan route (option only)
- Diligent's Vulkan backend can create a device and contexts without a swap chain
  (`CreateDeviceAndContextsVk`, separate `CreateSwapChainVk`) and wraps external images
  (`IRenderDeviceVk::CreateTextureFromVulkanImage`, "does not take ownership"). [V]
  https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/Graphics/GraphicsEngineVulkan/interface/RenderDeviceVk.h lines 82-90 and
  https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/Graphics/GraphicsEngineVulkan/interface/EngineFactoryVk.h lines 78-99.
- No AHardwareBuffer helper exists in those two headers. We would write the external-memory import ourselves
  (`VK_ANDROID_external_memory_android_hardware_buffer`; enable it through the device-extension list) and wrap the `VkImage`. [V for headers, U for backend sources]
- AHardwareBuffer: `AHardwareBuffer_allocate` is API 26, which equals our minSdk. Flags `GPU_COLOR_OUTPUT`
  (`GPU_FRAMEBUFFER`) and `GPU_SAMPLED_IMAGE`. GL import is via `eglGetNativeClientBufferANDROID` + `eglCreateImageKHR`
  (`EGL_ANDROID_get_native_client_buffer`, `EGL_ANDROID_image_native_buffer`). Vulkan sees it as external memory. [V]
  https://developer.android.com/ndk/reference/group/a-hardware-buffer
  (Khronos registry pages returned HTTP 403, so extension texts were not read.)
- Export impact:
  - projectM, MilkDrop, fluid, overlays and the safety composite are GL. The encoder input surface is an EGL window
    surface (`EncoderSurface.kt:62`).
  - So Vulkan could only produce an HDR AHB that the GL context samples before the final composite. Needs fence interop
    (`EGL_ANDROID_native_fence_sync` + Vulkan fd semaphores, or a blocking `glFinish`/`vkQueueWaitIdle` [U]) and a
    deterministic hand-off per frame.
  - It also adds a second driver stack to memory and thermal budget.
  - Whether a given driver lets GL sample an FP16 AHB is driver-dependent. [U]
- Verdict: do not start. It pays two driver matrices for the benefit of compute on devices that lack ES 3.1, and the blueprint keeps
  Vulkan out of the critical path (`docs/blueprint/OPEN_SOURCE.md`, "Engine selection gate").

### B4. DiligentFX on GLES 3.0 / 3.1 / 3.2 and Vulkan

Source check [V]: for each of Bloom, DepthOfField, TemporalAntiAliasing, ScreenSpaceReflection and ScreenSpaceAmbientOcclusion I grepped
`PostProcess/<Effect>/src/<Effect>.cpp` at tag `API256019` for `SHADER_TYPE_COMPUTE`, `ComputePipeline`, `DispatchCompute`
and `RWTexture`. **Zero matches.** Every pass is `SHADER_TYPE_PIXEL` with `FullScreenTriangleVS.fx`.
Base URL: https://raw.githubusercontent.com/DiligentGraphics/DiligentFX/API256019/PostProcess/<Effect>/src/<Effect>.cpp

| Effect | Compute? | Needs from the scene | Float render targets used | GLES notes |
|---|---|---|---|---|
| Bloom (mip-chain down/up, `Bloom.hpp:ComputeMipCount`) | No | HDR colour only | `R11G11B10_FLOAT` | uses border sampling if `BorderSamplingModeSupported` (ES 3.2/ext), else clamp; needs `ShaderBaseVertexOffset` or its index buffer |
| DepthOfField | No | depth, motion vectors (temporal CoC), camera attribs | `RG32_FLOAT`, `R32_FLOAT`, `R16_FLOAT`, `R16_UNORM` if supported, `RGBA16_FLOAT` | many full-resolution float targets: heavy bandwidth on tiled mobile GPUs [U] |
| TAA | No | motion vectors, current and previous depth, camera jitter | history buffer | needs `PostFXContext` reprojected depth + closest motion vectors |
| SSR | No (despite names like `ComputeHierarchicalDepthBuffer`, they are pixel shaders) | depth, normals/roughness material buffer | float + stencil | uses `TextureSubresourceViews` (ES 3.1 `texture_view`) with an ES 3.0 fallback path |
| SSAO | No | depth | float | same `TextureSubresourceViews` fallback |
| Tone-mapping helpers | n/a | none | n/a | `Shaders/PostProcess/ToneMapping/public/ToneMapping.fxh`: EXP, REINHARD, REINHARD_MOD, UNCHARTED2, FILMIC_ALU, LOGARITHMIC, ADAPTIVE_LOG, AGX, AGX_CUSTOM, PBR_NEUTRAL, COMMERCE. Pure shader text, portable to GLSL on either route |

Consequences:
- All of the above are float colour-renderable formats (`R11G11B10F`, `RG32F`, `R32F`, `RGBA16F`). On ES 3.0 that needs
  `EXT_color_buffer_float`; ES 3.2 includes it. [U, from the GLES spec; not re-fetched] Our `GlProber` already probes
  attachability/exact rendering/additive blending for `RGBA16F`, `R16F`, `RG16F`, `R32F` (`GlCaps.hpp:19-30`), so we can gate effects per device.
- The ES 3.0 viability of these effects is inferred from their WebGL code path (comments "Immutable samplers are required
  for WebGL to work properly", SSR/SSAO `TextureSubresourceViews` fallback). Nobody has published an Android run. [U]
- All FX shaders are HLSL. On GLES they are converted at runtime. Core README: "For OpenGL and OpenGLES modes, the source code
  will be converted to GLSL." [V] https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/README.md lines 331-335.
  The converter is the in-tree `HLSL2GLSLConverterLib`, which the GL backend links unless `DILIGENT_NO_HLSL` is set
  (`GraphicsEngineOpenGL/CMakeLists.txt` lines 194-199). [V for the CMake, U that this is the active runtime path]
  FX shaders are embedded in `DiligentFX` as generated headers (`convert_shaders_to_headers`), so no asset folder is needed. [V]
- TAA, DOF and motion blur need depth and motion-vector outputs from our scene pass (MRT). That is true on our own route too.
- SSR and SSAO fit a lit PBR scene, not an emissive tunnel. Skip them. Bloom, tone mapping, DOF and TAA are the useful four.
- On Vulkan the same passes work; Vulkan only adds compile-time SPIR-V (glslang) and extra backends. [U]

### B5. Cost

**APK size per ABI.** No published numbers exist (two searches). The README only says `DILIGENT_NO_GLSLANG` and
`DILIGENT_NO_HLSL` "significantly" reduce Vulkan/OpenGL backend size, "especially for mobile". [V]
https://raw.githubusercontent.com/DiligentGraphics/DiligentEngine/API256019/README.md lines 764-769.
Planning assumption **[U, guess, to be replaced by CI measurement]**:
- P1 (Core only, GLSL shaders, `NO_HLSL`): low single-digit MB per ABI stripped.
- P2 (Core + HLSL converter + Tools + FX): several times P1.
- The only certain delta is that Diligent adds a second `.so` per ABI (`libGraphicsEngineOpenGL.so`) next to `libgeode.so`,
  `libprojectM-4.so` and `libc++_shared.so`.

**Runtime shader compilation vs precompiling to drop runtimes**
- On GLES the GL backend sends GLSL straight to the driver (`glShaderSource`/`glCompileShader`, `ShaderGLImpl.cpp`
  `CompileShader`). [V] glslang and SPIRV-Cross are not in the GLES path.
- With Vulkan/WebGPU/Metal/archiver off they are not even built (B1). So "precompile to drop glslang/SPIRV-Cross" is moot for GLES-only.
- The real lever is `DILIGENT_NO_HLSL`, which removes the HLSL converter. That forces GLSL-only shaders and so excludes DiligentFX.
- Offline packaging exists (Archiver/Dearchiver for GL, `DearchiverGLImpl`), but the Archiver needs glslang on the build host,
  and it is a new build and test dependency. [U for GLES effect]
- Runtime hitch risk: every context creation re-runs conversion + driver compile (wallpaper is the worst case). Our
  `ProgramBinaryCache` (`core/viz/ProgramBinaryCache.hpp:20`) only caches our own programs. [U whether Diligent's render state cache helps GLES]

**CI build time.** No measurement. [U]
- Core-only build is a few hundred translation units per ABI. Adding Tools (libpng/libjpeg/libtiff/zlib/imgui/GLTF) and
  FX roughly doubles that.
- Existing CI: `debug-apk` job has a 45-minute timeout (`.github/workflows/android.yml:28-31`), AGP builds both ABIs
  (`app/build.gradle.kts:51-53`), `emulator` job has 20 minutes (`:163-167`).
- Mitigations: option OFF by default; separate dispatch workflow; ccache keyed on tag + NDK + flags; shallow submodule fetch.
  Measure with `.ninja_log` under `app/.cxx/**` per ABI.

### B6. Alternative: extend our GLES engine

What we already have on PR #9 [V]:
- Format and caps probing: `GlCaps.hpp:19-30` (`ProbedFormat` RGBA8/R16F/RG16F/RGBA16F/R32F/RGBA32UI with attachable/
  rendersExactly/blendsAdditively/filtersLinearly), `:44` (`maxColorAttachments`), `:63-95` (compute tier), `:106-115` (`FormatPlan`
  incl. `linearColorTarget`).
- ES 3.1 compute plumbing: `core/viz/Compute.hpp`, `core/viz/compute/SimPass.hpp:57`, `SimField`.
- Texture-state GPU particles that work on ES 3.0: `core/viz/fluid/FluidParticles.cpp`.
- Bloom, sunrays and blur shaders (MIT, from WebGL-Fluid-Simulation): `app/src/main/assets/shaders/fluid_bloom_*`, `fluid_sunrays_*`.
- Trails (`TrailPass.cpp`), composite and safety (`CompositePass.cpp`, `VisualSafety.hpp:16-25`), tiered budgets
  (`ThermalGovernor.hpp:11-12` `renderScale`, `optionalPasses`), `ProgramBinaryCache`, recovery in `Renderer::onSurfaceCreated`.
- Existing 3D-ish scenes: `rod_tunnel` (`CameraDirector.hpp`) and `prismatic_passage` (`SpatialCameraDirector.hpp`).

Gaps: `Framebuffer` is RGBA8 with no depth (`Framebuffer.cpp:18`), so no HDR chain, depth, MRT or motion vectors yet.

Permissive sources for the post stack (Apache-2.0 unless noted; check per-file headers before copying):
- Filament, licence verified as Apache-2.0 [V] https://raw.githubusercontent.com/google/filament/main/LICENSE; Android-first with a GLES backend [V] https://raw.githubusercontent.com/google/filament/main/README.md.
  Materials: `bloom` (bloomDownsample/Upsample/2x/9), `dof` (dofCoc, dofTiles, dofDilate, dofDownsample, dofMedian, dofCombine, dofMipmap), `antiAliasing/taa` and `fxaa`, `colorGrading`, `ssao`, `fog`, `fsr`, `sgsr`. [WF]
  https://github.com/google/filament/tree/main/filament/src/materials (and `/bloom`, `/dof`, `/antiAliasing`, `/colorGrading`).
  They are `.mat` material files, so shaders must be re-expressed as GLSL ES 3.00 passes.
- DiligentFX shaders as reference for Bloom/DOF/TAA/ToneMapping (HLSL, Apache-2.0). Port, do not link.
- Existing MIT fluid bloom already in the repo.
- Chromatic aberration and motion blur are small, first-principles passes. [P]
- Provenance rule: record URL, commit, files, licence hash, modifications (`docs/blueprint/OPEN_SOURCE.md`, "Adoption rules").

Comparison of the two main options:

| | Extend GLES (G) | Diligent (A/B) |
|---|---|---|
| Effort [P] | HDR/depth/MRT targets 2-3 d; bloom 2 d; DOF 4-6 d; motion blur 2-3 d; CA 0.5 d; trails to HDR 2 d; tonemap + safety last 1-2 d; tier/thermal budgets 3 d; export parity 3 d. About 18-25 d | Prototype 8-12 d; FX integration if go +10-15 d; plus the same scene/camera/tier/export work, which does not go away |
| Risk | Schedule; our own shader bugs | Build matrix, state fence, symbol clash, API churn, untested FX on Android |
| Look ceiling | Same; compute particles/volumetrics via existing ES 3.1 tier, texture-state fallback on ES 3.0 | Same; FX gives mature DOF/TAA faster |
| Export | Unchanged: everything runs inside `Renderer::render` into `targetFbo` | Works if scene output is a GL texture and Diligent never presents |
| projectM | No change | Needs fence around every Diligent section |
| Size/CI | None | See B5 |

### B7. Our seams (PR #9 branch, all file:line)

**EGL contexts and surfaces**

| Surface | Where |
|---|---|
| Live view | `engine/scenes/src/main/kotlin/dev/geode/render/VisualizerView.kt:12` (a `GLSurfaceView`); `:46` `setEGLContextClientVersion(3)`; `:50` `preserveEGLContextOnPause = true`; `:51` `setRenderer`; `:55` `RENDERMODE_WHEN_DIRTY`. No custom `EGLContextFactory` or config chooser exists in the repo (grep), so GLSurfaceView creates the context and window surface. Teardown job `:87-97`. |
| Live frame entry | `VisualizerRenderer.kt:155` `onSurfaceCreated` -> `NativeViz.kt:175` -> `geode_viz_jni.cpp:205-208` -> `core/api/geode_viz_api.cpp:210` -> `Renderer::onSurfaceCreated` `Renderer.cpp:370`. Per frame `VisualizerRenderer.kt:172-175` `onDrawFrame` -> `nativeViz.render(..., targetFbo = 0)` -> `geode_viz_jni.cpp:232-235` -> `geode_viz_api.cpp:230` -> `Renderer::render` `RendererFrame.cpp:301`. |
| Wallpaper | `app/src/main/java/dev/geode/wallpaper/VisualizerWallpaperService.kt:68-76` (`WallpaperGlSurfaceView : GLSurfaceView`, holder override); `:87-94` `setEGLContextClientVersion(3)`, `setRenderer(engine)`, `RENDERMODE_WHEN_DIRTY`, no `preserveEGLContextOnPause`; `:142-153` run state `onResume`/`onPause`; `:245-256` release on GL thread. |
| Export | `app/src/main/java/dev/geode/export/EncoderSurface.kt:31-64` `setUp`: ES3 config with `EGL_RECORDABLE_ANDROID` `0x3142` (`:36-57`), `eglCreateContext(..., EGL_NO_CONTEXT, ...)` so **not shared** with live (`:58-59`), `eglCreateWindowSurface` on the encoder input surface (`:62`); `:66-68` `makeCurrent`; `:70-76` presentation time and swap; `:79-89` release. Callers: `VideoExporter.kt:349-350` (create + make current), `:363-382` offscreen renderer spec, `:388-393` frame loop; `LoopRender.kt:390`. |
| Export renderer | `engine/scenes/.../render/offscreen/OffscreenSceneRenderer.kt:49-57` `prepare` (`setOffscreen(true)`, `surfaceCreated`, `surfaceChanged`, `setScene`), `:72-85` `renderFrame` -> `viz.render(timeMs / 1000.0, targetFbo)`. |
| Probe context | `core/viz/GlProfile.cpp:30-79` throw-away 1x1 pbuffer context; `:116-123` `profileInOwnContext`. Template for Option B. |

**Renderer, Scene, SceneRegistry, frame graph**
- `Scene` interface: `core/viz/Scene.hpp:18-52` (`id`, `family`, `init`, `setParams`, `resize`, `update(features, dt)`,
  `draw(timeSeconds)`, `release`, plus capability hooks). `SceneFamily` enum `:15`. `SceneHost` `:54-61`.
- `SceneRegistry`: `core/viz/SceneRegistry.hpp:15-30`; `SceneRegistry.cpp:22-30` shader id list (includes `tunnel`, `rod_tunnel`,
  `prismatic_passage` at `:23-29`); `:49-52` `knows`; `:54-67` `availableIds`; `:69-89` `create`.
- `Renderer`: `core/viz/Renderer.hpp:36` class comment "frame graph: scene pass -> trail -> composite -> target"; `:84` `render`;
  `:89-92` `Live`; members `:125-133` registry/trail/composite; `:141-142` `fboA_`, `fboB_`.
- Frame graph in `core/viz/RendererFrame.cpp`:
  - `:25-53` `beginFrame` (latches features/PCM, applies queued MilkDrop requests)
  - `:55-75` `resolveActiveScene`
  - `:77-121` `resolveParams`; `:113` switches between native-spatial and legacy motion params via `ownsNativeSpatialMotion()`
  - `:166-198` `drawSecondaryTargets` (layer/outgoing scene into `fboB_`)
  - `:200-215` `drawSceneTarget` (binds `fboA_`, clear or trail, `scene.update`, `scene.draw`)
  - `:217-280` `composite` (CompositePass into `targetFbo`, overlay last)
  - `:267-269` per-family grade gate via `core/viz/CompositeGrade.hpp:23-26` `gateFor` (a new family needs a decision here)
  - `:301-319` `render`
- Scene lifetime: `Renderer.cpp:487-494` `sceneFor`, `:496-527` `buildScene`, `:529-535` `releaseScenes`, `:436-455` `surfaceChanged`/`applyRenderScale`.
- New-scene registration touches: `SceneRegistry.cpp`, `core/CMakeLists.txt:64-91` sources, Kotlin `SceneIds.kt:49`,
  `SceneCapabilities.kt:81,103`, and `ParamScope.kt:97` for per-scene parameter scope.

**MilkDrop in the frame graph**
- `MilkdropScene` is an ordinary `Scene` (`MilkdropScene.hpp:22`). `draw()` at `MilkdropScene.cpp:210-265`: saves the draw FBO
  (`:213-214`), `resetFrameState()` (`:216`), `ensureEngine` (`:217`, `:114-149`), `projectm_opengl_render_frame_fbo(engine, frame_.fbo())`
  (`:232`, patched API: root `CMakeLists.txt:24-57`), resets state again and rebinds the previous FBO and viewport (`:237-239`),
  then draws a post program into the scene target (`:241-262`). Engine and GL objects are released in `release()` (`:311-324`).
- Offscreen/export PCM approximation: `RendererFrame.cpp:155`.
- Consequence for Diligent: a Diligent scene follows the same pattern. Read the bound draw FBO, run, reset, restore.

**Where `SpatialCameraDirector` plugs in**
- Header `core/viz/SpatialCameraDirector.hpp:12-76`, source `.cpp:59-157`. `step(Signals, Intent, dt)` at `.cpp:94-102` with a fixed
  1/120 s tick (`:11`); `corridorCenter(z)` `:84-92`; pose `:144-157`; fixture seed constructor `:61`.
  The default constructor uses `liveEntropy()` (`:23-38`), so live and export are **not** reproducible. D09 wants seeded behaviour,
  so use the fixture-seed constructor for the D08 comparison.
- Owned by `ShaderScene` only for `prismatic_passage`: `core/viz/scenes/ShaderScene.cpp:35` (`emplace`), `:107-120` (build signals/intent, step),
  `:278-288` `uploadSpatialCamera` (uniforms `uCameraPosition/Right/Up/Forward`, `uCorridorPhase`, `uSpatialBands`, `uSpatialForm`),
  `ShaderScene.hpp:39` (`ownsNativeSpatialMotion`), `:126` member. GLSL mirror in `app/src/main/assets/shaders/prismatic_passage_frag.glsl:25-33`.
- `rod_tunnel` uses the older `CameraDirector` (`ShaderScene.cpp:123-125`, `:172-176`).
- A Diligent scene would own a `SpatialCameraDirector` (or its generalisation) the same way and feed `frame().position/right/up/forward`
  into a real view matrix. The raymarched scene only needs a camera basis; a mesh scene needs matrices plus the previous frame's matrices for motion vectors.

---

## C. D08 prototype recipe

### C1. Pin

- DiligentEngine tag `API256019` = commit `138d8ec2c5ffd522d7cecce4871f3a0e75ed0b85` [WF]. Confirm with
  `git ls-remote --tags https://github.com/DiligentGraphics/DiligentEngine 'API256019*'`.
- Submodules to add (SHAs from the tag's tree [WF]; confirm with `git ls-tree`):
  - `third_party/diligent/DiligentCore` @ `0d88143`
  - `third_party/diligent/DiligentTools` @ `c82be22` (FX profile only)
  - `third_party/diligent/DiligentFX` @ `4b3c2bf` (FX profile only)
  - Samples: not needed.
  - EnTT v3.16.0 pinned beside them (FX profile only), used via `FETCHCONTENT_SOURCE_DIR_ENTT`.
- Core's own submodules: init only `ThirdParty/xxHash` for P1. Do not init the rest.
- Provenance: add entries to `docs/visualizer-v2/provenance.json` and `THIRD_PARTY_NOTICES`, including all licences in B1.

Two build profiles are measured so the owner sees the real cost of each:
- **P1 "core"**: DiligentCore only, GLSL shaders (`SHADER_SOURCE_LANGUAGE_GLSL_VERBATIM`), `DILIGENT_NO_HLSL=ON`, `DILIGENT_NO_GLSLANG=ON`. Post passes are our own GLSL (same files as the GLES route).
- **P2 "fx"**: P1 minus `NO_HLSL`, plus Tools and FX. Bloom, tone mapping, DOF and TAA come from DiligentFX.

### C2. CMake changes

Root `CMakeLists.txt`, before `add_subdirectory(core)` (currently `:83`):

```cmake
option(GEODE_DILIGENT_PROTOTYPE "D08: isolated Diligent Engine GLES prototype" OFF)
option(GEODE_DILIGENT_FX "D08: also build DiligentTools + DiligentFX (profile P2)" OFF)
if(GEODE_DILIGENT_PROTOTYPE)
    add_subdirectory(third_party/diligent)   # our wrapper dir; its variables do not leak back to the root
endif()
```

`third_party/diligent/CMakeLists.txt` (new, ours; the wrapper keeps Diligent's options and C++17 out of the root scope):

```cmake
cmake_minimum_required(VERSION 3.22.1)
set(CMAKE_CXX_STANDARD 17)                      # root sets 20 (CMakeLists.txt:4-6); Diligent sets none [V]
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_POLICY_VERSION_MINIMUM 3.5)           # insurance for CMake 4.x; remove if configure passes without it [U]
foreach(opt DILIGENT_NO_DIRECT3D11 DILIGENT_NO_DIRECT3D12 DILIGENT_NO_VULKAN DILIGENT_NO_WEBGPU
            DILIGENT_NO_METAL DILIGENT_NO_ARCHIVER DILIGENT_NO_RENDER_STATE_PACKAGER
            DILIGENT_NO_SUPER_RESOLUTION DILIGENT_NO_FORMAT_VALIDATION DILIGENT_NO_RADIENT)
    set(${opt} ON CACHE BOOL "" FORCE)
endforeach()
set(DILIGENT_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(DILIGENT_INSTALL_CORE OFF CACHE BOOL "" FORCE)
if(GEODE_DILIGENT_FX)
    set(DILIGENT_NO_HLSL OFF CACHE BOOL "" FORCE)
    set(DILIGENT_NO_GLSLANG ON CACHE BOOL "" FORCE)
    set(FETCHCONTENT_SOURCE_DIR_ENTT "${CMAKE_CURRENT_SOURCE_DIR}/entt" CACHE PATH "")
    set(FETCHCONTENT_FULLY_DISCONNECTED ON CACHE BOOL "" FORCE)
else()
    set(DILIGENT_NO_HLSL ON CACHE BOOL "" FORCE)
    set(DILIGENT_NO_GLSLANG ON CACHE BOOL "" FORCE)
endif()
# x86_64: Diligent adds -mavx2 to Release (core CMakeLists.txt ~477-483) [V]; prove it is absent in the output (C8).
add_subdirectory(DiligentCore EXCLUDE_FROM_ALL)
if(GEODE_DILIGENT_FX)
    add_subdirectory(DiligentTools EXCLUDE_FROM_ALL)
    add_subdirectory(DiligentFX EXCLUDE_FROM_ALL)
endif()
```

The root already forces `CMAKE_BUILD_TYPE Release` and 16 KB linker flags (`CMakeLists.txt:8-10,60`); Diligent inherits both.
Because the root toggles `BUILD_SHARED_LIBS` ON then OFF around projectM/kissfft (`:61,66`), set the Diligent subtree to
explicit `-shared`/`-static` targets only (Diligent already does).

**Link the facade, not the engine**, in `core/CMakeLists.txt` (after `:101`). A separate target keeps Diligent headers,
C++17 and any warning noise out of `geode_core`:

```cmake
if(GEODE_DILIGENT_PROTOTYPE)
    add_library(geode_diligent STATIC viz/diligent/DiligentFacade.cpp viz/diligent/TunnelPass.cpp)
    set_target_properties(geode_diligent PROPERTIES CXX_STANDARD 17 CXX_EXTENSIONS OFF)
    target_include_directories(geode_diligent PUBLIC viz/diligent/include)     # facade header has no Diligent types
    target_link_libraries(geode_diligent PRIVATE Diligent-GraphicsEngineOpenGL-shared GLESv3 EGL log android)
    if(GEODE_DILIGENT_FX)
        target_link_libraries(geode_diligent PRIVATE DiligentFX)
    endif()
    target_compile_definitions(geode_core PUBLIC GEODE_DILIGENT_PROTOTYPE=1)
    target_link_libraries(geode_core PRIVATE geode_diligent)
endif()
```

**Why a shared `libGraphicsEngineOpenGL.so`, not the static target [U, inferred from source]:**
`GLStubsAndroid.cpp` defines, at global scope, plain variables such as `PFNGLDISPATCHCOMPUTEPROC glDispatchCompute = nullptr;`
when the headers lack ES 3.1/3.2 declarations (`GLStubsAndroid.h:1169,1397`; `GLStubsAndroid.cpp:34-45`). [V]
Our code calls the real `glDispatchCompute` from `libGLESv3` (`core/viz/Compute.hpp` includes `<GLES3/gl31.h>`).
Linked statically into `libgeode.so` the symbols would collide and our calls would bind to Diligent's data variable.
Required checks: `llvm-nm -D --defined-only libGraphicsEngineOpenGL.so | grep ' gl[A-Z]'` shows no exported `gl*` variables, and
`libgeode.so` has no defined `gl*` symbols. Diligent's shared target restricts exports with a version script only for MinGW
(`GraphicsEngineOpenGL/CMakeLists.txt` lines 252-258), so add our own for Android.

### C3. Gradle changes

`app/build.gradle.kts` (existing block `:54-58` `externalNativeBuild.cmake.arguments += "-DANDROID_STL=c++_shared"`):
- Add a project property gate so default builds are unchanged:
  `if (providers.gradleProperty("geode.diligent").isPresent) arguments += listOf("-DGEODE_DILIGENT_PROTOTYPE=ON", ...)`,
  and `geode.diligent.fx` for `-DGEODE_DILIGENT_FX=ON`.
- Optional: `-DCMAKE_POLICY_VERSION_MINIMUM=3.5` here instead of in the wrapper if configure needs it for more than Diligent.
- Keep `ndkVersion = "30.0.16248370"` (`:43`), `cmake.version = "4.1.2"` (`:109`), ABIs `arm64-v8a`, `x86_64` (`:51-53`),
  `useLegacyPackaging = false` (`:113-117`). AGP packages `.so` files built by the CMake project (as it does for `libprojectM-4.so`,
  comment at `:105`). [U for the Diligent library; confirm with `unzip -l`.]
- `tools/release/check_native_alignment.py` already checks every ELF and APK entry (`android.yml:48`); no change.
- Debug-only Kotlin entry (no param-schema changes): add a hidden scene id to `SceneIds.kt` and `SceneCapabilities.kt:81,103`;
  `SceneRegistry::knows` returns true for it only when `GEODE_DILIGENT_PROTOTYPE` is defined. The existing scene setter
  (`VisualizerRenderer.requestedSceneId`, `:38-39`) and export scene id (`OffscreenSceneRenderer.sceneFactory.sceneId`, `:57`) then work as-is.
  Reuse existing `SceneParams` (`bloom`, `motionAmount`, `speed`, ...) for the prototype; adding fields means Kotlin wire changes.
- Wallpaper: add `preserveEGLContextOnPause = true` in the prototype build at `VisualizerWallpaperService.kt:89` to avoid rebuilding
  Diligent on every show (best effort).

### C4. New and changed code seams (all file:line are PR #9)

| Change | Where |
|---|---|
| Facade: owns the Diligent device/context, creates/destroys it, exposes `beginSection()/endSection()`, `renderTunnel(frameInputs, outputGlTexture)`; no Diligent types in the public header | new `core/viz/diligent/DiligentFacade.{hpp,cpp}` |
| Scene: `DiligentTunnelScene : Scene`, `draw()` mirrors `MilkdropScene.cpp:210-265` | new `core/viz/scenes/DiligentTunnelScene.cpp`, registered at `SceneRegistry.cpp:84` neighbourhood and `SceneRegistry.cpp:49-67` |
| Family | `Scene.hpp:15` add `Spatial` (or reuse `Shader`); decide post-grade gate at `CompositeGrade.hpp:23-26` |
| Context events | `Renderer::onSurfaceCreated` (`Renderer.cpp:370-375`): call `facade.onContextCreated()` right after `releaseScenes()` is replaced. `Renderer::releaseScenes` (`:529-535`) calls `facade.onContextDestroyed()` |
| Resize | `Renderer::applyRenderScale` (`:443-455`) already calls `scene->resize(renderWidth_, renderHeight_)`; recreate Diligent target wrappers there |
| Camera | scene owns `SpatialCameraDirector(fixtureSeed)` and calls `step` like `ShaderScene.cpp:107-120`; override `ownsNativeSpatialMotion()` (`Scene.hpp:33`) to get `nativeSpatialParams_` (`RendererFrame.cpp:113`) |
| Offscreen flag | `Scene` does not see `frameOffscreen_` directly; use `Renderer::setOffscreen` (`Renderer.cpp:119-125`) for the deterministic-time path, or pass a flag in `SceneHost` |

### C5. How live, wallpaper and export each work

**Live view** (GL thread of `VisualizerView`)
1. `onSurfaceCreated` (`VisualizerRenderer.kt:155`) reaches `Renderer::onSurfaceCreated` (`Renderer.cpp:370`). The old Diligent device is
   dropped without GL calls if `eglGetCurrentContext()` differs from the creation context; a new one is created lazily on first draw.
2. Each frame: `Renderer::render` -> `drawSceneTarget` binds `fboA_` (`RendererFrame.cpp:201-214`) -> `DiligentTunnelScene::draw`:
   begin section, Diligent renders HDR passes into its own textures, a final tone-map pass writes into a GL texture we own (or
   directly into the texture behind `fboA_`), end section (fence), then our own full-screen blit/draw into the bound `fboA_`.
3. `composite()` (`:217-280`) reads `fboA_.tex()`, applies the existing grade, overlay and **safety clamp last** into `targetFbo = 0`.
   GLSurfaceView swaps. Diligent never swaps, never touches FBO 0.

**Wallpaper**: identical native path (`VisualizerWallpaperService.kt:87-94` builds the same `VisualizerRenderer`). Differences: no
preserved context by default (full rebuild on each show) and `RENDERMODE_WHEN_DIRTY` paced by the wallpaper pacer. Measure
show-to-first-frame with and without `preserveEGLContextOnPause`.

**Export**
1. `EncoderSurface` (`EncoderSurface.kt:31-64`) creates a **separate** ES3 context (not shared) with the encoder window surface; `makeCurrent`
   (`VideoExporter.kt:350`).
2. `OffscreenSceneRenderer.prepare` (`:49-57`) creates a new `NativeViz`/`Renderer`, calls `setOffscreen(true)`, `surfaceCreated`, `surfaceChanged`.
   That Renderer owns its own Diligent device, attached to the encoder context (a surface is current, so no window pointer is needed). Two
   Diligent devices may be alive in one process (live + export); each is bound to its own thread/context.
3. `renderFrame` (`:72-85`) -> `Renderer::render(timeMs/1000.0, 0)`; Kotlin sets presentation time and `swapBuffers`
   (`VideoExporter.kt:391-393`). The Diligent scene must be deterministic in `(time, features, seed)`: no wall clock, no
   `liveEntropy()` (use the fixture-seed `SpatialCameraDirector`), no Diligent-internal frame counters that affect pixels.
4. Export reuses the same device across frames; recovery is not expected mid-export (context loss aborts the export, as today).

### C6. projectM and Fluid coexistence protocol

Wrap every Diligent section in an RAII `DiligentSection` inside `DiligentTunnelScene::draw`:

```
enter:  save GL_DRAW_FRAMEBUFFER_BINDING, GL_READ_FRAMEBUFFER_BINDING, viewport, scissor enable/box
        ctx->UpdateCurrentGLContext();   // returns false if no context -> skip the frame, log once
        ctx->InvalidateState();          // forget cached state; also unbinds program/pipeline/VAO/FBOs on GL
        (projectM state is thereby irrelevant to Diligent)
exit:   ctx->Flush();
        ctx->InvalidateState();                                  // unbind program/pipeline/VAO/FBOs again
        resetFrameState();                                       // Quad.cpp:36-52
        glDisable(GL_BLEND); glDisable(GL_PRIMITIVE_RESTART_FIXED_INDEX);
        glBlendFunc(...)/glDepthFunc(GL_LESS)/glDepthRangef(0,1)/glCullFace/glFrontFace  -> GL defaults
        unbind textures on units 0..N (2D, 2D_ARRAY, 3D, CUBE), samplers 0..max, UBO/SSBO/array/element buffers, VAO 0, program 0
        rebind saved FBOs, viewport, scissor
        glGetError drain (as MilkdropScene::drainEngineErrors, :298-309) -> report once
```
- Run `resetFrameState()` and the saved-state restore **before and after** Diligent, exactly as `MilkdropScene` does around projectM
  (`MilkdropScene.cpp:216,237-239`). The inverse also holds: projectM's leftovers cannot hurt Diligent because `InvalidateState()` starts every section.
- projectM stays untouched (still via `projectm_opengl_render_frame_fbo`, `MilkdropScene.cpp:232`). The two engines never share a draw call.
- Fluid scenes (`core/viz/fluid/*`, `FluidSceneBase`) live in the same context but own disjoint GL objects. Diligent may *read* a fluid
  velocity texture through `CreateTextureFromGLHandle(..., RESOURCE_STATE_SHADER_RESOURCE)`; it must never write it. Refresh wrappers on
  every Fluid re-create (resize/context event). Test transitions between Diligent scene, MilkDrop, and a Fluid scene via the existing
  transition path (`drawSecondaryTargets`, `RendererFrame.cpp:166-198`), where `fboB_` hosts the outgoing scene.
- Fallback inside the prototype is Option B (sibling shared context): in `enter`/`exit`, `eglMakeCurrent` to the sibling and back; skip the state
  save/restore; texture sharing replaces wrappers on the app side.

### C7. The comparison scene

"Glass Ring Tunnel" [P]: a curved tunnel with real depth so DOF, TAA/motion blur and fog are meaningful.
- Geometry: 48 instanced faceted rings along `SpatialCameraDirector::corridorCenter(z)` (`.cpp:84-92`); same period 96 and radius 2.1
  as `prismatic_passage` (`.hpp:14-15`) so cameras are interchangeable. One instanced draw plus a sky/emissive far plane.
- Shading: emissive HDR edges (values >1), simple fresnel/rim, depth fog. Written once as GLSL ES 3.00 and fed to both engines:
  GLES engine as `ShaderScene`-style programs, Diligent as `SHADER_SOURCE_LANGUAGE_GLSL_VERBATIM`. Identical uniform buffer layout.
- Outputs (MRT): RGBA16F colour, linear depth (R32F or R16F), screen-space velocity (RG16F) from previous/current view-projection.
- Inputs: identical recorded `GeodeFeatureFrame` stream (host fixture) and `SpatialCameraDirector(fixtureSeed)` so both engines see the same camera and audio.
- Tiers to compare:
  - T0: scene only, RGBA8 target.
  - T1: HDR + bloom + tone map.
  - T2: T1 + DOF + motion blur + chromatic aberration.
  - T3: T2 + trails/feedback + 20k dust particles + volumetric fog.
- Variants: **G** (our GLES engine, own GLSL post), **D1** (Diligent core + same GLSL post, P1), **D2** (Diligent + DiligentFX bloom/DOF/TAA/tone map, P2).
- Final tone map writes LDR into `fboA_`; existing composite and safety clamp stay last in all variants.

### C8. What CI measures

Add a dispatch-only workflow `diligent-prototype.yml` (and a PR label trigger). It builds twice at the same commit (flag off, flag on) for both ABIs:

| Metric | How |
|---|---|
| Configure + native build time | wall-clock around `./gradlew :app:externalNativeBuildRelease` (or debug as in `android.yml:38`); sum `.ninja_log` under `app/.cxx/**` per ABI; report cold and ccache-warm |
| Native size | per ABI, stripped `libgeode.so`, `libGraphicsEngineOpenGL.so`, `libprojectM-4.so`; APK size; `bundletool get-size total` (download size); `apkanalyzer files list --compressed`; `bloaty` top-20 compile units |
| Symbol hygiene | `llvm-nm -D --defined-only` on `libgeode.so` and `libGraphicsEngineOpenGL.so`: no `gl*` data symbols in `libgeode.so`; Diligent exports only `GetEngineFactoryOpenGL` |
| x86_64 ISA | `llvm-objdump -d libGraphicsEngineOpenGL.so | grep -c ymm` must be 0 |
| 16 KB | existing `python3 tools/release/check_native_alignment.py <apk>` (`android.yml:48`) |
| Min SDK | link succeeds against API 26 stubs |
| Functional smoke | extend `tools/android/run_emulator_checks.sh` (the `emulator` job, `android.yml:163-195`): select the hidden scene, run 300 frames, assert no `E/libEGL`, no `ERROR` from Diligent logs, non-black screenshot, 20 background/foreground cycles, 3 s export file validity. The emulator proves function only, not GPU performance (`docs/blueprint/CI.md`) |
| Leak check (debug build) | after N context cycles, sweep `glIsTexture/glIsBuffer/glIsFramebuffer` over names 1..4096 and compare counts; assert stable |
| Licences | `THIRD_PARTY_NOTICES` entries present for every built component |

Proposed pass numbers [P] (the docs only say "acceptable"): P1 adds at most 5 MB per ABI stripped; P2 at most 12 MB per ABI;
clean native build at most +6 min per full two-ABI build; warm-cache delta at most +1 min.

### C9. What the owner checks on a device

At minimum one Adreno and one Mali device; add a PowerVR/ES 3.0-only device if available.
1. Install the three builds (G, D1, D2). Run T0 to T3 at the same resolution and render scale. Record p50/p95 frame time (in-app `FramePacer` stats,
   `GL_EXT_disjoint_timer_query` where `GlProfile.capabilities.timerQueries` is Trusted, Perfetto) and 10-minute sustained thermal tier.
2. Blind side-by-side stills and video: is the Diligent output visibly better at equal cost? (Gate G4.)
3. 50x rotate/fold, 50x background/foreground; check no flicker, no black frames, memory flat.
4. Wallpaper: 100 show/hide cycles; time to first frame; compare with and without preserved context.
5. MilkDrop to tunnel and back, 100 times, via the transition system; projectM output uncorrupted, no GL errors.
6. Export a 60 s 1080x1920 30 fps clip with the tunnel scene on each device; file plays, A/V in sync, no dropped frames; run twice and compare checksums.
7. A 16 KB-page device or emulator image.
8. Reduced-motion and flash-safety toggles behave (safety clamp is unaffected).

### C10. Go / no-go rule (pre-registered) [P]

- Hard fails (any one means no-go for production):
  - G1 does not build on arm64-v8a and x86_64 with NDK r30 and CMake 4.1.2 within two fix iterations.
  - G5 visible projectM, fluid or transition corruption that Option B does not fix.
  - G6 leaked GL objects or wrong-context deletes across 50 context cycles.
  - G7 export that is invalid or non-deterministic on device.
- Judgement gates with numbers: G2 size, G3 frame time (p95 within +10% of G at the same tier; no earlier thermal throttling), G4 demonstrable look gain, CI time.
- If G4 is not a clear win, no-go even when everything else passes, because the fallback has equal ceiling at lower cost.
- Outcome documented in `docs/blueprint/` with the measured table. Retaining the GLES engine is the baseline either way (`DELIVERY_PLAN.md`, D08).

---

## D. Fallback design if no-go: "Spatial pipeline in our GLES engine"

Keeps D09's contract unchanged (camera director, tiers, post order: scene -> trails -> bloom -> tone map -> overlays -> safety clamp last).

1. **Render targets**: replace the fixed RGBA8 `Framebuffer` (`Framebuffer.cpp:18`) by `FramebufferDesc {colour format, depth (D24 or D32F texture), MRT count, mip count}`.
   Choose formats from `FormatPlan` / `GlProfile.formats` (`GlCaps.hpp:99-115`): `RGBA16F` -> `R11G11B10F` -> `RGBA8` with dither; MRT limited by
   probed `maxColorAttachments` (`:44`); use `glInvalidateFramebuffer` (already in `Framebuffer.cpp:47-54`) to save bandwidth on tilers.
2. **`SpatialScene` base class** (next to `Scene.hpp`): owns a generalised `CameraRig` (seeded; wraps `SpatialCameraDirector`/`CameraDirector`), publishes view/projection
   plus previous matrices and optional depth/velocity outputs. `ShaderScene`'s `prismatic_passage` adapter becomes the first user (`ShaderScene.cpp:107-120,278-288`).
3. **`PostStack`** (new `core/viz/post/`), ordered, tier-aware:
   - Trails/feedback: extend `TrailPass.cpp` to HDR.
   - Bloom: dual-filter down/up chain, starting from `fluid_bloom_*` shaders.
   - DOF: CoC from linear depth, half-resolution gather (Filament `dof*` and DiligentFX DOF as references).
   - Motion blur: per-pixel velocity from MRT.
   - Chromatic aberration: radial, cheap, last before tone map.
   - Tone map: selectable ACES-fit / AgX / PBR Neutral (DiligentFX `ToneMapping.fxh`, Filament `colorGrading`).
   - Then the existing `CompositePass`, overlays and safety clamp.
   - Budget each pass by `ThermalGovernor` (`optionalPasses`, `renderScale`) and `Renderer::applyRenderScale`.
4. **GPU particles**: ES 3.1 compute path via existing `SimPass`/`SimField`; ES 3.0 fallback via texture-state particles as in `FluidParticles.cpp`.
5. **Volumetrics**: depth-aware raymarched fog in the scene pass plus sunray-style shafts post pass (reuse `fluid_sunrays_*`); optional froxel grid under the compute tier.
6. **Export parity**: no change. Everything runs inside `Renderer::render` into `targetFbo`.
7. **Tests**: host C++ tests for pass ordering, tier budgets and format fallback (pattern of `core/tests/*`); `tools/shaderpreview` for shader previews; emulator smoke.
8. **Provenance**: every ported shader gets a `provenance.json` entry with URL, commit, licence hash and modifications.

Effort estimate [P]: 18-25 engineer-days for the post stack and HDR targets, 5-8 for particles, 4-6 for volumetrics, before scene art.

---

## E. Risks

| # | Risk | Impact | Mitigation / detection |
|---|---|---|---|
| 1 | Diligent's global GL stub variables clash with real `gl*` functions if the GL backend is linked statically into `libgeode.so` | crash on first compute call | build GL backend as its own `.so` with a version script; `llvm-nm` gate in CI [U, inferred] |
| 2 | Diligent leaves GL state (samplers, enables, bindings, primitive restart) and unbinds FBO/VAO/program | projectM or fluid corruption | `DiligentSection` fence; Option B as fallback; 100-transition test |
| 3 | Releasing Diligent objects with a different EGL context current deletes wrong GL names | random corruption after context loss | abandon-on-mismatch teardown; release before context loss; name-sweep leak test |
| 4 | Wallpaper rebuilds the context on every show; HLSL conversion + driver compile stall | visible hitch, battery | `preserveEGLContextOnPause`, P1 GLSL shaders, measure; program binary caching |
| 5 | Untested toolchain: NDK r30, CMake 4.1.2, C++20 inheritance, minSdk 26, x86_64; upstream CI is NDK 27 / CMake 3.22.1 / armv7+arm64 / minSdk 28 | build churn | wrapper CMake with C++17, `CMAKE_POLICY_VERSION_MINIMUM` insurance, first-day compile spike |
| 6 | `-mavx2` baked into x86_64 Release | SIGILL on non-AVX2 x86_64 devices/emulators | override and `ymm` scan in CI |
| 7 | DiligentFX not Android-CI'd; needs float render targets (`EXT_color_buffer_float`), depth, motion vectors; many full-res R32F/RG32F targets | broken or slow on ES 3.0 devices, thermal | probe-gated per device (existing `GlProber`), test on Adreno + Mali, drop DOF temporal path if too heavy |
| 8 | FX dependency chain: DiligentTools (libpng/libjpeg/libtiff/zlib/imgui), EnTT FetchContent, vendored dawn/abseil/OpenXR/DXC in DiligentCore clone | size, CI time, supply-chain surface, offline-build failure | submodule pins, `FETCHCONTENT_SOURCE_DIR_ENTT`, shallow init, P1-only if P2 fails |
| 9 | Upstream churn: API tags every few months, current master moves daily (Oct 2026: Vulkan SDK 1.4.363, super-resolution) | upgrade cost | pin tag; no tracking of master |
| 10 | Reversed-Z, clip control and other desktop-style features are not available on GLES (no standard `glClipControl` in ES 3.x) | small precision loss | use standard Z in prototype [U] |
| 11 | Two Diligent devices (live + export) double HDR target memory | OOM on low-RAM devices | export tier caps, release live targets during export if needed |
| 12 | `SpatialCameraDirector` default seed is live entropy | non-reproducible export and non-comparable runs | fixture seed in prototype; D09 seeding requirement |
| 13 | Licence/provenance: many third-party components if P2 | compliance gap | notices + provenance entries per component before merging |
| 14 | Facts marked [WF], [U], [P] here are not verified | wrong pin, wrong estimate | verify tag/SHAs and submodule SHAs with `git ls-remote`/`git ls-tree`; replace estimates with CI numbers |
| 15 | Time-box overrun | schedule | stop at the pre-registered gates; fallback in D is independent |

---

## Source list (all facts above cite one of these)

- DiligentEngine: https://github.com/DiligentGraphics/DiligentEngine (tags, commits, releases pages; README at tag `API256019`)
- DiligentCore at tag `API256019`: README, `CMakeLists.txt`, `ThirdParty/CMakeLists.txt`, `.gitmodules`, `Graphics/GraphicsEngineOpenGL/{CMakeLists.txt,readme.md,interface/*.h,src/GLContextAndroid.cpp,src/GLContextState.cpp,src/GLStubsAndroid.cpp,src/DeviceContextGLImpl.cpp,src/EngineFactoryOpenGL.cpp,src/RenderDeviceGLImpl.cpp,src/ShaderGLImpl.cpp}`, `Graphics/GraphicsEngineVulkan/interface/*.h`, `.github/workflows/build-android.yml`, `BuildTools/Android/*` (all under https://raw.githubusercontent.com/DiligentGraphics/DiligentCore/API256019/)
- DiligentFX at `API256019`: `README.md`, `CMakeLists.txt`, `PostProcess/*`, `Shaders/PostProcess/ToneMapping/public/ToneMapping.fxh` (https://raw.githubusercontent.com/DiligentGraphics/DiligentFX/API256019/)
- DiligentTools at `API256019`: `CMakeLists.txt`, `.gitmodules`, `ThirdParty/CMakeLists.txt` (https://raw.githubusercontent.com/DiligentGraphics/DiligentTools/API256019/)
- Issues: https://github.com/DiligentGraphics/DiligentCore/issues/58 and https://github.com/DiligentGraphics/DiligentCore/issues/61
- Filament: https://github.com/google/filament (LICENSE, README, `filament/src/materials/*`)
- Android AHardwareBuffer: https://developer.android.com/ndk/reference/group/a-hardware-buffer
- Repo (PR #9 branch `origin/codex/account-visual-foundation`): all `file:line` references; `origin/main:docs/blueprint/{OPEN_SOURCE,DELIVERY_PLAN,ARCHITECTURE}.md`, `docs/rebuild/PLAN.md`

Not reachable this session: GitHub API (repository not enabled for the GitHub tools and `gh`), Khronos registry pages (HTTP 403).
Raw files were fetched with `curl` through the proxy and `github.com` HTML pages through WebFetch.
