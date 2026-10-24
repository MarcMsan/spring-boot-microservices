# Inventory commits the reservation, then holds the HTTP response longer than the order
# service is willing to wait. The order stays PENDING. The same request is then retried
# with inventory answering immediately. Stock moves once.

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

Write-Host "Starting inventory with a 5s response delay"
docker compose -f docker-compose.yml -f docker-compose.lost-response.yml up -d --force-recreate inventory-service | Out-Host
Wait-Health "http://localhost:8081/actuator/health"
Wait-Health "http://localhost:8080/actuator/health"

$product = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/products" -ContentType "application/json" -Body (@{
    name = "Delayed cable"
    price = 8.00
    availableQuantity = 5
} | ConvertTo-Json)

$key = [guid]::NewGuid().ToString()
$body = @{
    customerEmail = "lost@example.com"
    items = @(@{ productId = $product.id; quantity = 2 })
} | ConvertTo-Json -Depth 5 -Compress

$pending = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/orders" -ContentType "application/json" -Headers @{ "Idempotency-Key" = $key } -Body $body
$pendingOrder = $pending.Content | ConvertFrom-Json
$stockWhilePending = Invoke-RestMethod -Uri "http://localhost:8081/api/products/$($product.id)"
Write-Host "First response: HTTP $([int]$pending.StatusCode) status=$($pendingOrder.status) stock=$($stockWhilePending.availableQuantity)"
if ($pendingOrder.status -ne "PENDING" -or $stockWhilePending.availableQuantity -ne 3) {
    throw "Expected a pending order and stock already reduced from 5 to 3."
}

Write-Host "Removing the delay and retrying the same request"
docker compose up -d --force-recreate --no-deps inventory-service | Out-Host
Wait-Health "http://localhost:8081/actuator/health"

$recovered = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/orders" -ContentType "application/json" -Headers @{ "Idempotency-Key" = $key } -Body $body
$order = $recovered.Content | ConvertFrom-Json
$stock = Invoke-RestMethod -Uri "http://localhost:8081/api/products/$($product.id)"
$reservation = Invoke-RestMethod -Uri "http://localhost:8081/api/reservations/$($order.id)"
Write-Host "Retry: HTTP $([int]$recovered.StatusCode) status=$($order.status) total=$($order.total) stock=$($stock.availableQuantity) reservation=$($reservation.status)"
if ($order.status -ne "CONFIRMED" -or $order.total -ne 16.00 -or $stock.availableQuantity -ne 3 -or $reservation.status -ne "RESERVED") {
    throw "Retry reserved stock again or did not confirm at the original price."
}
