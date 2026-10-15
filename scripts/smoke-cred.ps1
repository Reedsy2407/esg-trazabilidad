# Credentials of the production smoke-test account, kept encrypted with Windows DPAPI
# (only this Windows user on this machine can decrypt them), outside the repo:
#   %LOCALAPPDATA%\esg-smoke\cred.xml   { Email (clear), Password (DPAPI string) }
#
#   -Mode Init            create the file with a new smoke-<random>@esgtrazabilidad.pe and no password yet
#   -Mode Set             read the password from STANDARD INPUT and store it encrypted
#   -Mode Get -Field Email|Password   print one field (for a $(...) capture only, never on a screen)
#   -Mode Check           print "ok" when the file decrypts and has both fields (never the password)
#
# The password is never an argument, never written to a log and never printed by Init/Set/Check.
param(
    [Parameter(Mandatory)][ValidateSet('Init', 'Set', 'Get', 'Check')][string]$Mode,
    [ValidateSet('Email', 'Password')][string]$Field
)
$ErrorActionPreference = 'Stop'
$dir = Join-Path $env:LOCALAPPDATA 'esg-smoke'
$file = Join-Path $dir 'cred.xml'

function Read-Cred {
    if (-not (Test-Path $file)) { throw "no credential file at $file (run -Mode Init)" }
    Import-Clixml $file
}
function Save-Cred([string]$email, [string]$encrypted) {
    New-Item -ItemType Directory -Force -Path $dir | Out-Null
    [pscustomobject]@{ Email = $email; Password = $encrypted } | Export-Clixml $file
}

switch ($Mode) {
    'Init' {
        $b = New-Object byte[] 4
        [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b)
        $suffix = -join ($b | ForEach-Object { $_.ToString('x2') })
        Save-Cred "smoke-$suffix@esgtrazabilidad.pe" ''
        Write-Output 'initialised (e-mail chosen, password not set yet)'
    }
    'Set' {
        $cred = Read-Cred
        $plain = [Console]::In.ReadToEnd().Trim()
        if ($plain.Length -lt 12) { throw 'password from stdin is too short' }
        $secure = ConvertTo-SecureString -String $plain -AsPlainText -Force
        Save-Cred $cred.Email (ConvertFrom-SecureString -SecureString $secure)
        Write-Output 'password stored (DPAPI)'
    }
    'Get' {
        $cred = Read-Cred
        if ($Field -eq 'Email') { Write-Output $cred.Email; break }
        if (-not $cred.Password) { throw 'no password stored yet' }
        $secure = ConvertTo-SecureString -String $cred.Password
        $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
        try { Write-Output ([Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)) }
        finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr) }
    }
    'Check' {
        $cred = Read-Cred
        if (-not $cred.Email -or -not $cred.Password) { throw 'e-mail or password missing' }
        $secure = ConvertTo-SecureString -String $cred.Password
        Write-Output "ok (e-mail $($cred.Email), password decrypts, $($secure.Length) characters)"
    }
}
