// Pixel similarity is image retrieval, not object recognition or model confidence.
export function pixelDescriptor(data) {
  const histogram = Array(64).fill(0), spatial = [];
  let count = 0;
  for (let i = 0; i < data.length; i += 4) {
    const alpha = data[i + 3] / 255;
    const rgb = [0, 1, 2].map(c => (data[i + c] * alpha + 255 * (1 - alpha)) / 255);
    histogram[Math.min(3, Math.floor(rgb[0] * 4)) * 16 + Math.min(3, Math.floor(rgb[1] * 4)) * 4 + Math.min(3, Math.floor(rgb[2] * 4))]++;
    spatial.push(...rgb);
    count++;
  }
  return { histogram: histogram.map(n => n / Math.max(count, 1)), spatial };
}
export function imageDistance(a, b) {
  const color = a.histogram.reduce((sum, n, i) => sum + Math.abs(n - b.histogram[i]), 0) / 2;
  const shape = a.spatial.reduce((sum, n, i) => sum + Math.abs(n - b.spatial[i]), 0) / Math.max(a.spatial.length, 1);
  return color * 0.55 + shape * 0.45;
}
