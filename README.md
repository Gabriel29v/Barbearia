# Barbearia Pente Fino

O site pode ser publicado na Vercel, com a API Java no Render e os dados no PostgreSQL do Supabase. O mesmo backend continua podendo ser executado localmente com SQLite.

## Publicação: Vercel + Render + Supabase

Você precisará de contas no [GitHub](https://github.com/), [Supabase](https://supabase.com/), [Render](https://render.com/) e [Vercel](https://vercel.com/). Não coloque senhas, URLs com senha ou credenciais SMTP nos arquivos do projeto.

### 1. Envie o projeto ao GitHub

Crie um repositório no GitHub e envie os arquivos do projeto. O build da Vercel publica somente os arquivos do site em `dist`; o código Java e o banco SQLite local não fazem parte dessa pasta publicada.

### 2. Crie o banco PostgreSQL no Supabase

1. Crie um projeto no Supabase e guarde a senha do banco em local seguro.
2. Abra **Connect** ou **Settings > Database** e selecione a conexão **Session pooler**. Ela é adequada para conexões persistentes do backend hospedado.
3. Anote o host, a porta, o usuário e o nome do banco informados pelo Supabase. O usuário do pooler costuma incluir o identificador do projeto, por exemplo `postgres.abcdefghijkl`.
4. O backend cria as tabelas `customers` e `appointments` automaticamente ao iniciar; não é necessário executar SQL manualmente.

O Supabase guarda os dados, mas não recebe conexões do navegador. Somente a API no Render deve receber as credenciais do banco.

### 3. Publique a API no Render

1. No Render, escolha **New > Blueprint** e conecte o repositório do GitHub.
2. O Render encontrará o arquivo `render.yaml` e usará o `Dockerfile` para compilar e iniciar a API.
3. Ao configurar o Blueprint, preencha as variáveis solicitadas:

| Variável | Valor |
| --- | --- |
| `DATABASE_URL` | `jdbc:postgresql://HOST:PORTA/postgres?sslmode=require` usando os dados do Session pooler |
| `DATABASE_USERNAME` | Usuário do Session pooler fornecido pelo Supabase |
| `DATABASE_PASSWORD` | Senha do banco do Supabase |
| `ADMIN_EMAIL` | E-mail que será usado para entrar como administrador |
| `ADMIN_PASSWORD` | Senha forte para o administrador |
| `ALLOWED_ORIGINS` | Domínio da Vercel, sem barra no final; configure depois de criar o site, por exemplo `https://barbearia.vercel.app` |

O Render já recebe as configurações de porta e endereço de escuta pelo `render.yaml`. SMTP é opcional e pode ser configurado no passo 6. Depois da publicação, copie a URL pública do serviço, por exemplo `https://pente-fino-api.onrender.com`. Confira se `https://SUA-URL/api/health` responde `{"status":"UP"}`.

### 4. Publique o site na Vercel

1. Na Vercel, escolha **Add New > Project** e importe o mesmo repositório.
2. Use a pasta raiz como **Root Directory**. O projeto já define o comando de build e a pasta de saída no `vercel.json`.
3. Antes de publicar, adicione a variável de ambiente `BARBER_API_BASE_URL` com a URL pública do Render, sem `/api` no final, por exemplo `https://pente-fino-api.onrender.com`. Essa URL é pública; não coloque nela usuário, senha ou chaves.
4. Publique o projeto e copie o domínio atribuído pela Vercel.

O build copia para `dist` apenas HTML, CSS, JavaScript e imagens. Ele gera `api-config.js` durante a publicação usando `BARBER_API_BASE_URL`.

### 5. Autorize o domínio Vercel na API

No serviço do Render, abra **Environment** e configure `ALLOWED_ORIGINS` com o domínio exato da Vercel, sem caminho nem barra final, como `https://barbearia.vercel.app`. Para autorizar mais de um domínio, separe-os por vírgula. Salve e aguarde a nova publicação da API.

Se mudar `BARBER_API_BASE_URL` na Vercel ou `ALLOWED_ORIGINS` no Render, publique novamente o serviço correspondente.

### 6. Configure o envio de e-mails (opcional)

No serviço do Render, adicione as variáveis SMTP. Exemplo de configuração STARTTLS:

| Variável | Exemplo |
| --- | --- |
| `SMTP_HOST` | `smtp.gmail.com` |
| `SMTP_PORT` | `587` |
| `SMTP_SECURITY` | `starttls` |
| `SMTP_USER` | Conta de e-mail usada para enviar |
| `SMTP_PASSWORD` | Senha de app ou credencial SMTP do provedor |
| `SMTP_FROM_EMAIL` | Endereço remetente autorizado pelo provedor |
| `SMTP_FROM_NAME` | `Barbearia Pente Fino` |

Salve e aguarde a nova publicação. O sistema atual envia a confirmação para o cliente depois que ele faz um agendamento. Para Gmail, normalmente é necessário criar uma senha de app. Nunca use a senha normal da conta nem coloque essa credencial no GitHub ou na Vercel.

### 7. Faça um teste de ponta a ponta

1. Abra o domínio Vercel e crie uma conta de cliente.
2. Entre como cliente, escolha um serviço e horário e conclua um agendamento.
3. Confira se o agendamento aparece no calendário do administrador e, se configurou SMTP, se a confirmação chegou ao e-mail do cliente.
4. Entre na aba **Administrador** com `ADMIN_EMAIL` e `ADMIN_PASSWORD`.

O banco na nuvem começa vazio. O arquivo SQLite local não é enviado nem migrado automaticamente; os clientes precisarão criar contas novamente, a menos que os dados sejam migrados separadamente. Em planos gratuitos, o serviço do Render pode demorar para responder após um período sem uso.

## Executar localmente com SQLite

Requisitos: JDK 17 ou superior e Maven 3.8 ou superior. Na primeira execução, o Maven baixa as dependências do projeto.

No PowerShell, na pasta do projeto:

```powershell
$env:ADMIN_EMAIL = "administrador@seudominio.com"
$env:ADMIN_PASSWORD = "escolha-uma-senha-forte-com-12-caracteres-ou-mais"
mvn spring-boot:run
```

Mantenha o terminal aberto e acesse `http://127.0.0.1:8000`. O banco local fica em `data/barbearia.sqlite3`. Para desenvolvimento local, `api-config.js` deixa o endereço da API vazio e o navegador usa a API no mesmo servidor.

## Notas de segurança

- As credenciais do Supabase, do administrador e do SMTP devem ficar somente nas variáveis privadas do Render.
- `BARBER_API_BASE_URL` é apenas o endereço público da API; a Vercel não precisa receber credenciais do banco.
- Configure o domínio público da Vercel em `ALLOWED_ORIGINS` no Render.
- Faça backups regulares do banco Supabase e não compartilhe publicamente a URL de conexão se ela incluir credenciais.
