#pragma once

// Host-only forward declaration for the opaque pointer in geode_api.h.
// MotionField does not call Android asset APIs. This supplies no fake API
// behavior and is never on the Android application's include path.
typedef struct AAssetManager AAssetManager;
