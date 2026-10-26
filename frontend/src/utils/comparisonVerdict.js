// A transparent comparison of current catalogue fields, not generated AI advice.
export function generateComparisonVerdict(products) {
  if (!products || products.length < 2) return null;
  const priced = products.filter(p => Number(p.price) > 0).sort((a,b) => Number(a.price)-Number(b.price));
  const rated = products.filter(p => Number(p.reviewCount) > 0 && Number(p.averageRating) > 0)
    .sort((a,b) => Number(b.averageRating)-Number(a.averageRating) || Number(b.reviewCount)-Number(a.reviewCount));
  const bestPriceProduct = priced[0] || null;
  const topPerf = rated[0] || null;
  const bestValue = rated.filter(p => Number(p.price)>0)
    .sort((a,b) => Number(b.averageRating)/Number(b.price)-Number(a.averageRating)/Number(a.price))[0] || null;
  return {
    bestPriceProduct, topPerf, bestValue,
    summary: `${bestPriceProduct ? bestPriceProduct.name + ' has the lowest listed price.' : 'No valid prices are listed.'} ${topPerf ? topPerf.name + ' has the highest recorded customer rating.' : 'There are no recorded customer ratings to compare.'} These rules do not measure hardware performance or guarantee suitability.`,
    productStrengths: products.map(product => ({
      product, isLowest: product.id === bestPriceProduct?.id, isTopPerf: product.id === topPerf?.id,
      isBestVal: product.id === bestValue?.id,
      badge: product.id === bestPriceProduct?.id ? 'Lowest price' : product.id === topPerf?.id ? 'Highest rated' : 'Compare specifications',
      reason: Number(product.reviewCount)>0 ? `${product.averageRating}/5 from ${product.reviewCount} recorded reviews.` : 'No customer reviews recorded; inspect the listed specifications.',
    })),
  };
}
