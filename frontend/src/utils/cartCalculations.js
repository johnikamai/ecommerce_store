export function bundlePercentFor(distinctProducts) {
  if (distinctProducts >= 4) return 0.15;
  if (distinctProducts === 3) return 0.1;
  if (distinctProducts === 2) return 0.05;
  return 0;
}

export function nextBundleTier(distinctProducts) {
  if (distinctProducts < 2) return 2;
  if (distinctProducts < 3) return 3;
  if (distinctProducts < 4) return 4;
  return 0;
}