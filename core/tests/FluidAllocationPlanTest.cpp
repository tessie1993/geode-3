#include "viz/fluid/AllocationPlan.hpp"

#include <cassert>
#include <cstdio>

namespace {
using namespace geode::viz::fluid;

void failedUltraAllocationReachesUsefulFloorWithinBudget() {
    const int expectedSim[] = {256, 128, 64, 32};
    const int expectedDye[] = {1024, 512, 256, 128};
    for (int attempt = 0; attempt < kAllocationAttempts; ++attempt) {
        const auto request = allocationRequest(256, 1024, attempt);
        assert(request.sim == expectedSim[attempt]);
        assert(request.dye == expectedDye[attempt]);
    }
}

void floorsAndBadAttemptIndicesDoNotCreateZeroOrHugeGrids() {
    const auto small = allocationRequest(1, 2, 3);
    assert(small.sim == 32 && small.dye == 128);
    const auto first = allocationRequest(128, 512, -1);
    assert(first.sim == 128 && first.dye == 512);
    const auto last = allocationRequest(128, 512, 100);
    assert(last.sim == 32 && last.dye == 128);
    assert(particleSideRequest(1024, 0) == 1024);
    assert(particleSideRequest(1024, 3) == 128);
    assert(particleSideRequest(160, 3) == 80);
}

void retryFootprintOnlyDecreases() {
    for (int sim : {64, 96, 128, 192, 256}) {
        int previous = sim * sim;
        for (int attempt = 0; attempt < kAllocationAttempts; ++attempt) {
            const auto request = allocationRequest(sim, sim * 4, attempt);
            assert(request.sim * request.sim <= previous);
            assert(request.dye >= 128);
            previous = request.sim * request.sim;
        }
    }
}
}  // namespace

int main() {
    failedUltraAllocationReachesUsefulFloorWithinBudget();
    floorsAndBadAttemptIndicesDoNotCreateZeroOrHugeGrids();
    retryFootprintOnlyDecreases();
    std::puts("Fluid allocation plan tests passed");
}
