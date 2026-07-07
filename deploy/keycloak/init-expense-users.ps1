param(
    [string] $KeycloakBaseUrl = 'http://192.168.23.66:18080',
    [string] $Realm = 'expense-flow',
    [string] $AdminUsername = 'admin',
    [string] $AdminPassword = 'admin',
    [string] $UserPassword = 'ExpenseFlow123!'
)

$ErrorActionPreference = 'Stop'

function Invoke-KeycloakJson {
    param(
        [ValidateSet('Get', 'Post', 'Put')]
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
    Invoke-RestMethod @options
}

function Get-JwtPayload {
    param([string] $Token)

    $payload = $Token.Split('.')[1].Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($payload)) | ConvertFrom-Json
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
$requiredRoles = @(
    'EMPLOYEE',
    'REVIEWER',
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
    username = 'employee01';
    firstName = 'Expense';
    lastName = 'Employee';
    email = 'employee01@example.local';
    roles = @('EMPLOYEE');
}
$users += [pscustomobject]@{
    username = 'reviewer01';
    firstName = 'Expense';
    lastName = 'Reviewer';
    email = 'reviewer01@example.local';
    roles = @('REVIEWER');
}
$users += [pscustomobject]@{
    username = 'admin01';
    firstName = 'Expense';
    lastName = 'Admin';
    email = 'admin01@example.local';
    roles = $requiredRoles;
}

foreach ($user in $users) {
    $encodedUsername = [uri]::EscapeDataString($user.username)
    $existingUsers = @(Invoke-KeycloakJson `
            -Method Get `
            -Uri "$KeycloakBaseUrl/admin/realms/$Realm/users?username=$encodedUsername&exact=true" `
            -Headers $headers)

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
                -Headers $headers)
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

    $token = (Invoke-RestMethod `
            -Method Post `
            -Uri "$KeycloakBaseUrl/realms/$Realm/protocol/openid-connect/token" `
            -ContentType 'application/x-www-form-urlencoded' `
            -Body @{
                grant_type = 'password'
                client_id = 'expense-web'
                username = $user.username
                password = $UserPassword
            }).access_token
    $payload = Get-JwtPayload -Token $token
    $businessRoles = @($payload.realm_access.roles | Where-Object { $requiredRoles -contains $_ })

    [pscustomobject]@{
        username = $user.username;
        roles = (($businessRoles | Sort-Object) -join ',');
    }
}
