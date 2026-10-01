// Returns the id of the currently signed-in customer (stored on login/register).
// Falls back to customer 1 (the original demo account) if not signed in yet.
export function getCustomerId() {
  const id = Number(localStorage.getItem('customerId'));
  return Number.isFinite(id) && id > 0 ? id : 1;
}

export function getCustomerName() {
  return localStorage.getItem('customerName');
}