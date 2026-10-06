# Barbearia Pente Fino

## Requisitos

- JDK 17 ou superior.
- Maven 3.8 ou superior.

Confira se os dois comandos estão disponíveis no PowerShell:

```powershell
$env:JAVA_HOME = Join-Path $env:USERPROFILE "ferramentas\jdk-17"
& "$env:JAVA_HOME\bin\java.exe" -version
& "$env:USERPROFILE\ferramentas\apache-maven-3.9.16\bin\mvn.cmd" -version
```

## Executar no Windows

Abra o PowerShell na pasta do projeto e defina as credenciais do administrador:

```powershell
Set-Location "C:\Users\gabriel.nascimento\Downloads\Barbearia"
$env:ADMIN_EMAIL = "administrador@seudominio.com"
$env:ADMIN_PASSWORD = "escolha-uma-senha-forte-com-12-caracteres-ou-mais"
$env:JAVA_HOME = Join-Path $env:USERPROFILE "ferramentas\jdk-17"
$env:Path = "$env:JAVA_HOME\bin;$env:USERPROFILE\ferramentas\apache-maven-3.9.16\bin;$env:Path"
& "$env:USERPROFILE\ferramentas\apache-maven-3.9.16\bin\mvn.cmd" spring-boot:run
```

Na primeira execução, o Maven baixa as dependências. Mantenha essa janela aberta e acesse `http://127.0.0.1:8000`. Clientes podem entrar pela aba **Cliente** ou criar uma conta na aba **Criar conta**. Para abrir o calendário administrativo, selecione **Administrador** e entre com o mesmo e-mail e a mesma senha definidos no PowerShell ao iniciar o servidor.

O backend é Java 17 com Spring Boot; o site continua em HTML, CSS e JavaScript. O banco SQLite fica em `data/barbearia.sqlite3`. Se você já tinha dados criados pelo backend Python anterior, o Java usa o mesmo arquivo e as mesmas tabelas.

## Confirmação por e-mail

O envio de confirmações é opcional. Para ativá-lo, configure as credenciais SMTP na mesma janela do PowerShell, antes de iniciar a aplicação:

```powershell
$env:SMTP_HOST = "smtp.seuprovedor.com"
$env:SMTP_PORT = "587"
$env:SMTP_SECURITY = "starttls"
$env:SMTP_USER = "usuario-smtp"
$env:SMTP_PASSWORD = "senha-ou-token-smtp"
$env:SMTP_FROM_EMAIL = "agendamentos@seudominio.com"
$env:SMTP_FROM_NAME = "Barbearia Pente Fino"
mvn spring-boot:run
```

Para provedores que exigem SSL implícito, use `SMTP_SECURITY = "ssl"` e a porta indicada pelo provedor, geralmente `465`. Sem SMTP ou se o envio falhar, o agendamento continua salvo e aparece no calendário do administrador; a página informa que o e-mail não foi enviado. Nunca publique senhas de administrador ou SMTP no código-fonte.

O servidor escuta em `127.0.0.1` por padrão, para desenvolvimento local. Para publicar o site, configure HTTPS, proteja as variáveis de ambiente e faça cópias de segurança do banco; não exponha este servidor de desenvolvimento diretamente à internet.
