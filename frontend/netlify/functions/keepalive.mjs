export const schedule = "*/5 * * * *"

export default async () => {
  try {
    const res = await fetch("https://ecommerce-backend-o9eg.onrender.com/api/products")
    return new Response(`Backend ${res.status}`, { status: 200 })
  } catch (err) {
    return new Response(`Backend unreachable: ${err.message}`, { status: 200 })
  }
}