/**
 * Multi-dimensional comparison verdict generator.
 * Analyzes compared products for:
 * 1. Price Champion (Best Budget)
 * 2. Performance / Rating Leader (Top Performance)
 * 3. Overall Value Verdict (Best Value Index)
 * 4. Tailored recommendations for specific buyer needs
 */

export function generateComparisonVerdict(products) {
  if (!products || products.length < 2) return null;

  // 1. Lowest price
  const validPrices = products.filter((p) => p.price != null && Number(p.price) > 0);
  const bestPriceProduct = validPrices.reduce((prev, curr) =>
    Number(curr.price) < Number(prev.price) ? curr : prev, validPrices[0]);

  // 2. Performance / Rating Leader
  // Looks at rating, reviews, and specifications presence
  const scoredProducts = products.map((p) => {
    const rating = Number(p.averageRating || 0);
    const reviews = Number(p.reviewCount || 0);
    const price = Number(p.price || 1);
    const specsLen = (p.specifications || '').length;

    // Performance heuristic score
    const perfScore = (rating * 2) + Math.min(5, reviews / 20) + (specsLen > 20 ? 3 : 0);

    // Value index = performance score divided by price tier
    const valueIndex = (rating > 0 ? rating : 3.5) / (price / 10000);

    return { product: p, perfScore, valueIndex, price, rating };
  });

  scoredProducts.sort((a, b) => b.perfScore - a.perfScore);
  const topPerf = scoredProducts[0].product;

  scoredProducts.sort((a, b) => b.valueIndex - a.valueIndex);
  const bestValue = scoredProducts[0].product;

  // Determine key strengths per product
  const productStrengths = products.map((p) => {
    const isLowest = p.id === bestPriceProduct?.id;
    const isTopPerf = p.id === topPerf?.id;
    const isBestVal = p.id === bestValue?.id;

    let badge = 'Balanced Choice';
    if (isLowest && isBestVal) badge = 'Best Value & Price Pick';
    else if (isLowest) badge = 'Best Budget Pick';
    else if (isTopPerf) badge = 'Top Performance';
    else if (isBestVal) badge = 'Best Value Index';

    let reason = '';
    const specs = (p.specifications || '').toLowerCase();
    if (specs.includes('16gb') || specs.includes('32gb') || specs.includes('core i7') || specs.includes('ryzen 7')) {
      reason = 'High-capacity multitasking, programming, and power workloads.';
    } else if (p.category === 'Laptops') {
      reason = 'Optimal balance for work, coding, and general computing.';
    } else if (p.category === 'Mobiles') {
      reason = 'Great battery endurance, display clarity, and daily reliability.';
    } else if (isLowest) {
      reason = 'Most cost-effective with low barrier to purchase.';
    } else {
      reason = 'Solid user reviews and dependable catalog track record.';
    }

    return {
      product: p,
      badge,
      isLowest,
      isTopPerf,
      isBestVal,
      reason,
    };
  });

  // Overall human-readable summary
  let summary = '';
  if (bestPriceProduct.id === topPerf.id) {
    summary = `${topPerf.name} is the clear winner across both budget and performance in this comparison.`;
  } else {
    summary = `If your priority is saving money, **${bestPriceProduct.name}** is the budget champion. For top-tier specifications and capability, **${topPerf.name}** is our top recommendation.`;
  }

  return {
    bestPriceProduct,
    topPerf,
    bestValue,
    productStrengths,
    summary,
  };
}
