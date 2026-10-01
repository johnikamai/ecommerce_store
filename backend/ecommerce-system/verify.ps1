param([string]$Base = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'

function WebApi([string]$m, [string]$path, $body, [string]$tok) {
  $hdrs = @{}
  if ($tok) { $hdrs['Authorization'] = "Bearer $tok" }
  $p = @{ Method = $m; Uri = "$Base$path"; ErrorAction = 'Stop' }
  if ($body) { $p['ContentType'] = 'application/json'; $p['Body'] = ($body | ConvertTo-Json -Compress) }
  if ($hdrs.Count) { $p['Headers'] = $hdrs }
  try {
    $r = Invoke-WebRequest @p
    return [pscustomobject]@{ Status = [int]$r.StatusCode; Ok = $true; Body = $r.Content; Json = if ($r.Content) { try { $r.Content | ConvertFrom-Json } catch { $null } } }
  } catch {
    $code = $null; if ($_.Exception.Response) { $code = [int]$_.Exception.Response.StatusCode }
    return [pscustomobject]@{ Status = $code; Ok = $false; Body = $_.Exception.Message; Json = $null }
  }
}

$user = 'cust' + (Get-Random -Minimum 1000 -Maximum 9999)
$email = $user + '@test.dev'
Write-Output "=== register customer: $user ==="
$r = WebApi 'Post' '/api/auth/register' @{ username = $user; email = $email; password = 'Passw0rd!x'; role = 'CUSTOMER' } $null
Write-Output "status=$($r.Status)"
$otp = $null; if ($r.Json -and $r.Json.devOtp) { $otp = [string]$r.Json.devOtp }
Write-Output "devOtp: $($r.Json.devOtp)  message: $($r.Json.message)"
if ($r.Status -ne 200 -and $r.Status -ne 201) { Write-Output ("press: " + $r.Body); exit 1 }

Write-Output "=== verify-otp ==="
if ($otp) {
  $r2 = WebApi 'Post' '/api/auth/verify-otp' @{ username = $user; otp = $otp } $null
  Write-Output "status=$($r2.Status) body=$($r2.Body)"
} else { Write-Output "NO OTP RETURNED - cannot proceed"; exit 1 }

Write-Output "=== login ==="
$r3 = WebApi 'Post' '/api/auth/login' @{ username = $user; password = 'Passw0rd!x' } $null
Write-Output "status=$($r3.Status)"
$tok = if ($r3.Json -and $r3.Json.token) { [string]$r3.Json.token } else { $null }
Write-Output "token present: $([bool]$tok)  role: $($r3.Json.role)"
if (-not $tok) { Write-Output ($r3.Body); exit 1 }

Write-Output ""
Write-Output "===== SECURITY CHECKS (customer token) ====="
Write-Output ("--- customer GET /api/orders           expect 403 (admin-only list) -> " + (WebApi 'Get' '/api/orders' $null $tok).Status)
Write-Output ("--- customer GET /api/orders/1         expect 403 (IDOR/IDOR-blocked) -> " + (WebApi 'Get' '/api/orders/1' $null $tok).Status)
Write-Output ("--- customer GET /api/orders/customer/"+$Json)  # placeholder suppress

# customer id 23 must be bugtest's, not this fresh user's; use /customer/{?} with own? we don't know own id yet.
# We can only test the auth-level outcomes reliably. Show own-customer route is authenticated (403 vs 401 semantics):
$r4 = WebApi 'Get' '/api/returns/customer/23' $null $tok
Write-Output ("--- customer GET /api/returns/customer/23  (cross-user, expect 403 owner-block) -> " + $r4.Status)
$r5 = WebApi 'Get' '/api/notifications/customer/23' $null $tok
Write-Output ("--- customer GET /api/notifications/customer/23 (cross-user, expect 403 owner-block) -> " + $r5.Status)
