param(
    [string] $KeycloakBaseUrl = 'http://192.168.23.66:18080',
    [string] $Realm = 'my-expense-agent',
    [string] $AdminUsername = 'admin',
    [string] $AdminPassword = 'admin',
    [string] $UserPassword = 'MyExpense123!',
    [string] $BackendClientSecret = $env:KEYCLOAK_BACKEND_CLIENT_SECRET
)

$ErrorActionPreference = 'Stop'

function Invoke-KeycloakJson {
    param(
        [ValidateSet('Get', 'Post', 'Put', 'Delete')]
        [string] $Method,
        [string] $Uri,
        [hashtable] $Headers,
        [object] $Body = $null
    )

    $options = @{
        Method = $Method
        Uri = $Uri
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $options.ContentType = 'application/json'
        $options.Body = ConvertTo-Json -InputObject $Body -Depth 8
    }
    try {
        Invoke-RestMethod @options
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        throw "Keycloak Admin API 调用失败：$Method $Uri，HTTP $status"
    }
}

$adminToken = (Invoke-RestMethod `
        -Method Post `
        -Uri "$KeycloakBaseUrl/realms/master/protocol/openid-connect/token" `
        -ContentType 'application/x-www-form-urlencoded' `
        -Body @{
            grant_type = 'password'
            client_id = 'admin-cli'
            username = $AdminUsername
            password = $AdminPassword
        }).access_token

$headers = @{ Authorization = "Bearer $adminToken" }

$webClients = @(Invoke-KeycloakJson `
        -Method Get `
        -Uri "$KeycloakBaseUrl/admin/realms/$Realm/clients?clientId=my-expense-agent-web" `
        -Headers $headers)
if ($webClients.Count -ne 1) {
    throw 'my-expense-agent-web client 不存在或不唯一'
}
$webClient = Invoke-KeycloakJson `
    -Method Get `
    -Uri "$KeycloakBaseUrl/admin/realms/$Realm/clients/$($webClients[0].id)" `
    -Headers $headers
$webClient.redirectUris = @(
    'http://localhost:25105/*',
    'http://127.0.0.1:25105/*',
    'http://192.168.23.66:25105/*'
)
$webClient.webOrigins = @(
    'http://localhost:25105',
    'http://127.0.0.1:25105',
    'http://192.168.23.66:25105'
)
$webClient.standardFlowEnabled = $true
$webClient.directAccessGrantsEnabled = $false
if ($null -eq $webClient.attributes) {
    $webClient | Add-Member -NotePropertyName attributes -NotePropertyValue @{} -Force
}
$webClient.attributes.'pkce.code.challenge.method' = 'S256'
Invoke-KeycloakJson `
    -Method Put `
    -Uri "$KeycloakBaseUrl/admin/realms/$Realm/clients/$($webClients[0].id)" `
    -Headers $headers `
    -Body $webClient | Out-Null

if (-not [string]::IsNullOrWhiteSpace($BackendClientSecret)) {
    $backendClients = @(Invoke-KeycloakJson `
            -Method Get `
            -Uri "$KeycloakBaseUrl/admin/realms/$Realm/clients?clientId=my-expense-agent-backend" `
            -Headers $headers)
    if ($backendClients.Count -ne 1) {
        throw 'my-expense-agent-backend client 不存在或不唯一'
    }
    $backendClient = Invoke-KeycloakJson `
        -Method Get `
        -Uri "$KeycloakBaseUrl/admin/realms/$Realm/clients/$($backendClients[0].id)" `
        -Headers $headers
    $backendClient | Add-Member -NotePropertyName secret -NotePropertyValue $BackendClientSecret -Force
    Invoke-KeycloakJson `
        -Method Put `
        -Uri "$KeycloakBaseUrl/admin/realms/$Realm/clients/$($backendClients[0].id)" `
        -Headers $headers `
        -Body $backendClient | Out-Null
}
$requiredRoles = @(
    'STUDENT',
    'ADVISOR',
    'COLLEGE_REVIEWER',
    'FINANCE_ADMIN',
    'PROMPT_AUTHOR',
    'PROMPT_REVIEWER',
    'PROMPT_PUBLISHER',
    'AUDITOR'
)

foreach ($role in $requiredRoles) {
    try {
        Invoke-KeycloakJson -Method Get -Uri "$KeycloakBaseUrl/admin/realms/$Realm/roles/$role" -Headers $headers | Out-Null
    }
    catch {
        Invoke-KeycloakJson -Method Post -Uri "$KeycloakBaseUrl/admin/realms/$Realm/roles" -Headers $headers -Body @{ name = $role } | Out-Null
    }
}

$roleByName = @{}
foreach ($role in $requiredRoles) {
    $roleByName[$role] = Invoke-KeycloakJson -Method Get -Uri "$KeycloakBaseUrl/admin/realms/$Realm/roles/$role" -Headers $headers
}

$users = @()
$users += [pscustomobject]@{
    username = 'student01';
    firstName = 'Ming';
    lastName = 'Li';
    email = 'student01@example.local';
    roles = @('STUDENT');
}
$users += [pscustomobject]@{
    username = 'advisor01';
    firstName = 'Project';
    lastName = 'Advisor';
    email = 'advisor01@example.local';
    roles = @('ADVISOR');
}
$users += [pscustomobject]@{
    username = 'collegeReviewer01';
    firstName = 'College';
    lastName = 'Reviewer';
    email = 'college-reviewer01@example.local';
    roles = @('COLLEGE_REVIEWER');
}
$users += [pscustomobject]@{
    username = 'finance01';
    firstName = 'Campus';
    lastName = 'Finance';
    email = 'finance01@example.local';
    roles = @('FINANCE_ADMIN', 'PROMPT_AUTHOR', 'PROMPT_REVIEWER', 'PROMPT_PUBLISHER');
}
$users += [pscustomobject]@{
    username = 'auditor01';
    firstName = 'Campus';
    lastName = 'Auditor';
    email = 'auditor01@example.local';
    roles = @('AUDITOR');
}
foreach ($user in $users) {
    $encodedUsername = [uri]::EscapeDataString($user.username)
    $existingUsers = @(Invoke-KeycloakJson `
            -Method Get `
            -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users?username=$encodedUsername&exact=true" `
            -Headers $headers | Where-Object { -not [string]::IsNullOrWhiteSpace($_.id) })

    $userBody = @{
        username = $user.username;
        firstName = $user.firstName;
        lastName = $user.lastName;
        email = $user.email;
        enabled = $true;
        emailVerified = $true;
        requiredActions = @();
    }

    if ($existingUsers.Count -eq 0) {
        Invoke-KeycloakJson `
            -Method Post `
            -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users" `
            -Headers $headers `
            -Body $userBody | Out-Null
        $existingUsers = @(Invoke-KeycloakJson `
                -Method Get `
                -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users?username=$encodedUsername&exact=true" `
                -Headers $headers | Where-Object { -not [string]::IsNullOrWhiteSpace($_.id) })
        if ($existingUsers.Count -ne 1) {
            throw "用户创建后无法唯一定位：$($user.username)"
        }
    }
    else {
        Invoke-KeycloakJson `
            -Method Put `
            -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users/$($existingUsers[0].id)" `
            -Headers $headers `
            -Body $userBody | Out-Null
    }

    $userId = $existingUsers[0].id
    Invoke-KeycloakJson `
        -Method Put `
        -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users/$userId/reset-password" `
        -Headers $headers `
        -Body @{
            type = 'password';
            value = $UserPassword;
            temporary = $false;
        } | Out-Null

    $roleMappings = @($user.roles | ForEach-Object { $roleByName[$_] | Select-Object id, name })
    Invoke-KeycloakJson `
        -Method Post `
        -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users/$userId/role-mappings/realm" `
        -Headers $headers `
        -Body $roleMappings | Out-Null

    $assignedRoles = @(Invoke-KeycloakJson `
            -Method Get `
            -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users/$userId/role-mappings/realm" `
            -Headers $headers)
    $businessRoles = @($assignedRoles.name | Where-Object { $requiredRoles -contains $_ })

    [pscustomobject]@{
        username = $user.username;
        roles = (($businessRoles | Sort-Object) -join ',');
    }
}

if (-not [string]::IsNullOrWhiteSpace($BackendClientSecret)) {
    $serviceToken = (Invoke-RestMethod `
            -Method Post `
            -Uri "$KeycloakBaseUrl/realms/$Realm/protocol/openid-connect/token" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{
                grant_type = 'client_credentials'
                client_id = 'my-expense-agent-backend'
                client_secret = $BackendClientSecret
            }).access_token
    if ([string]::IsNullOrWhiteSpace($serviceToken)) {
        throw 'my-expense-agent-backend 服务账号令牌获取失败'
    }
}
