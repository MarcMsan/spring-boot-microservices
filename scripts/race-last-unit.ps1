$ErrorActionPreference = "Stop"

$product = Invoke-RestMethod -Method Post -Uri "http://localhost:8081/api/products" -ContentType "application/json" -Body (@{
    name = "Last unit"
    price = 5.00
    availableQuantity = 1
} | ConvertTo-Json)

Write-Host "Product $($product.id) stock=$($product.availableQuantity)"

$script = {
    param($ProductId, $Key)
    $body = @{
        customerEmail = "race@example.com"
        items = @(@{ productId = $ProductId; quantity = 1 })
    } | ConvertTo-Json -Depth 5 -Compress
    $headers = @{
        "Idempotency-Key" = $Key
        "X-Correlation-Id" = $Key
    }
    try {
        $response = Invoke-WebRequest -Method Post -Uri "http://localhost:8080/api/orders" -ContentType "application/json" -Headers $headers -Body $body
        $order = $response.Content | ConvertFrom-Json
        [PSCustomObject]@{ StatusCode = [int]$response.StatusCode; OrderStatus = $order.status; OrderId = $order.id }
    } catch {
        $detail = $_.ErrorDetails.Message
        [PSCustomObject]@{ StatusCode = 0; OrderStatus = "ERROR"; OrderId = $detail }
    }
}

$jobs = 1..8 | ForEach-Object {
    Start-Job -ScriptBlock $script -ArgumentList $product.id, ([guid]::NewGuid().ToString())
}

$results = $jobs | Wait-Job | Receive-Job
$jobs | Remove-Job
$results | Format-Table -AutoSize

$confirmed = @($results | Where-Object { $_.OrderStatus -eq "CONFIRMED" }).Count
$rejected = @($results | Where-Object { $_.OrderStatus -eq "REJECTED" }).Count
Write-Host "CONFIRMED=$confirmed REJECTED=$rejected"

$after = Invoke-RestMethod -Uri "http://localhost:8081/api/products/$($product.id)"
Write-Host "Stock after race=$($after.availableQuantity)"

if ($confirmed -ne 1 -or $after.availableQuantity -ne 0) {
    throw "Expected exactly one confirmation and zero stock."
}
