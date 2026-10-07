#pragma once
#include <algorithm>

namespace geode::viz::fluid {

// A failed GPU allocation must have a finite retry budget. Keeping the floors
// above zero also preserves useful flow detail on narrow portrait surfaces.
struct GridRequest {
    int sim;
    int dye;
};

constexpr int kAllocationAttempts = 4;

inline GridRequest allocationRequest(int sim, int dye, int attempt) {
    const int divisor = 1 << std::clamp(attempt, 0, kAllocationAttempts - 1);
    return {std::max(sim / divisor, 32), std::max(dye / divisor, 128)};
}

inline int particleSideRequest(int side, int attempt) {
    const int divisor = 1 << std::clamp(attempt, 0, kAllocationAttempts - 1);
    return std::max(side / divisor, 80);
}

}  // namespace geode::viz::fluid
