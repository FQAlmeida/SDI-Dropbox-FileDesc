## Configuração

Adicionar chaves das contas do Dropbox na raiz do projeto
Os arquivos devem ser:

- dbU1Token.properties
- dbU2Token.properties
- dbU3Token.properties

## Executar o Projeto

Na pasta raiz do projeto

```bash
# Building
mvn package

# Exec Monitor (Deve ser na ens5)
mvn exec:java -pl monitor -Dexec.mainClass="com.wsudesc.otavio.Monitor" < servidor.in

# Exec Client
mvn exec:java -pl client -Dexec.mainClass="com.wsudesc.otavio.Client"
```
