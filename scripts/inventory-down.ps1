# Stops Inventory Service after creating a product, places an order, then starts it again.
# The order stays PENDING while inventory is unreachable and later becomes CONFIRMED.
# Replaying the same Idempotency-Key does not create a second order.

$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")

function Wait-Health([string]$Url) {
    $deadline = (Get-Date).AddSeconds(90)
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

Wait-Health "http://localhost:8081/actuator/health"
Wait-Health "http://localhost:8080/actuator/health"

$product = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/products" -ContentType "application/json" -Body (@{
    name = "Recovery cable"
    price = 3.00
    availableQuantity = 4
} | ConvertTo-Json)
Write-Host "Created product $($product.id)"

docker stop vido-inventory | Out-Host

$key = [guid]::NewGuid().ToString()
$body = @{
    customerEmail = "down@example.com"
    items = @(@{ productId = $product.id; quantity = 1 })
} | ConvertTo-Json -Depth 5 -Compress
$pending = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/orders" -ContentType "application/json" -Headers @{ "Idempotency-Key" = $key } -Body $body
$pendingOrder = $pending.Content | ConvertFrom-Json
Write-Host "While inventory is down: HTTP $([int]$pending.StatusCode) status=$($pendingOrder.status)"
if ($pendingOrder.status -eq "CONFIRMED") {
    throw "Order was confirmed while inventory was down."
}

docker start vido-inventory | Out-Host
Wait-Health "http://localhost:8081/actuator/health"

$recovered = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/orders" -ContentType "application/json" -Headers @{ "Idempotency-Key" = $key } -Body $body
$order = $recovered.Content | ConvertFrom-Json
$stock = Invoke-RestMethod -Uri "http://localhost:8081/api/products/$($product.id)"
Write-Host "After recovery: HTTP $([int]$recovered.StatusCode) status=$($order.status) stock=$($stock.availableQuantity) order=$($order.id)"
if ($order.status -ne "CONFIRMED" -or $order.id -ne $pendingOrder.id -or $stock.availableQuantity -ne 3) {
    throw "Recovery did not confirm the original order exactly once."
}
