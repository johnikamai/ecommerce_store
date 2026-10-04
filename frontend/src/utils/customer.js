// Returns the id of the currently signed-in customer (stored on login/register).
// Anonymous visitors have no customer profile.
export function getCustomerId() {
  const id = Number(localStorage.getItem('customerId'));
  return Number.isFinite(id) && id > 0 ? id : null;
}

export function getCustomerName() {
  return localStorage.getItem('customerName');
}