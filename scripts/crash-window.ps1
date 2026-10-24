$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

function Wait-Health([string]$Url) {
    $deadline = (Get-Date).AddSeconds(120)
    do {
        try {
            $health = Invoke-RestMethod -Uri $Url
            if ($health.status -eq "UP") { return }
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)
    throw "Timed out waiting for $Url"
}

Write-Host "Starting order service with the post-reservation failure flag"
$env:FAIL_BEFORE_STATUS_UPDATE = "true"
docker compose -f docker-compose.yml -f docker-compose.crash-window.yml up -d --force-recreate --no-deps order-service | Out-Host
Wait-Health "http://localhost:8081/actuator/health"
Wait-Health "http://localhost:8080/actuator/health"

$product = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/products" -ContentType "application/json" -Body (@{
    name = "Crash-window cable"
    price = 6.50
    availableQuantity = 6
} | ConvertTo-Json)

$key = [guid]::NewGuid().ToString()
$body = @{
    customerEmail = "crash@example.com"
    items = @(@{ productId = $product.id; quantity = 2 })
} | ConvertTo-Json -Depth 5 -Compress
$pending = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/orders" -ContentType "application/json" -Headers @{ "Idempotency-Key" = $key } -Body $body
$pendingOrder = $pending.Content | ConvertFrom-Json
$stock = Invoke-RestMethod -Uri "http://localhost:8081/api/products/$($product.id)"
Write-Host "After simulated crash: HTTP $([int]$pending.StatusCode) status=$($pendingOrder.status) stock=$($stock.availableQuantity)"
if ($pendingOrder.status -ne "PENDING" -or $stock.availableQuantity -ne 4) {
    throw "Expected PENDING with stock reserved."
}

Write-Host "Restarting order service without the failure flag"
Remove-Item Env:FAIL_BEFORE_STATUS_UPDATE -ErrorAction SilentlyContinue
docker compose up -d --force-recreate --no-deps order-service | Out-Host
Wait-Health "http://localhost:8080/actuator/health"

$deadline = (Get-Date).AddSeconds(40)
do {
    Start-Sleep -Seconds 2
    $order = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/$($pendingOrder.id)"
} while ($order.status -eq "PENDING" -and (Get-Date) -lt $deadline)

$stockAfter = Invoke-RestMethod -Uri "http://localhost:8081/api/products/$($product.id)"
Write-Host "Recovered status=$($order.status) total=$($order.total) stock=$($stockAfter.availableQuantity)"
if ($order.status -ne "CONFIRMED" -or $stockAfter.availableQuantity -ne 4) {
    throw "Recovery did not reach a single confirmation."
}
