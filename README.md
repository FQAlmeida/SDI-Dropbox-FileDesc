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
java -jar monitor/target/monitor-1.0-SNAPSHOT.jar < servidor.in

# Exec Client
java -jar client/target/client-y.jar
```
