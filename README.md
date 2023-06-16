# FILEDesc

Trabalho desenvolvido para a disciplina de SDI - 23/01.

Alunos:
- Gilson Sohn
- Otávio Almeida

Orientador:
- Maurício A. Pillon

Universidade de Estado de Santa Catarina.

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
