# FeuTimer 1.4.0 para Android TV

O ficheiro FeuTimer-AndroidTV.apk instala a aplicação na Android TV, Google TV ou box Android com Android 6 ou posterior. Uma pen USB e um gestor de ficheiros na TV são suficientes. Podes descarregar o APK diretamente da publicação no GitHub.

## Instalação por pen USB

1. Descarrega o APK ou extrai o ficheiro FeuTimer-TV.zip no computador.
2. Copia FeuTimer-AndroidTV.apk para uma pen USB e liga-a à TV.
3. No gestor de ficheiros da TV, abre o APK. Se o Android pedir autorização, permite a instalação de aplicações desta origem para esse gestor e volta ao APK.
4. Escolhe **Instalar** ou **Atualizar**. No fim, escolhe **Abrir**. A aplicação também aparece na lista de aplicações como FEU BJJ Cronómetro.
5. No menu inicial, escolhe **Treino** ou **Competição**. Em **Treino → Treinos guardados → Criar treino**, define as rondas, a luta e o descanso e escolhe **Guardar** para acrescentar um treino. Para uma sessão imediata, abre **Treino → Temporizador personalizado**. O placar está em **Competição**.

## Instalação pelo Windows com ADB

1. Instala Android SDK Platform-Tools, disponibiliza o comando adb no PATH e liga a TV ao computador por um método de depuração suportado pelo aparelho.
2. Autoriza este computador na TV. Executa `adb devices -l` e confirma que a TV aparece como `device`. Se aparecerem vários dispositivos, define `ANDROID_SERIAL` para o identificador da tua TV antes de continuar.
3. Extrai FeuTimer-TV.zip e executa **Instalar-na-TV.cmd**. O instalador usa `adb install -r` para conservar os dados de uma instalação compatível e abre a aplicação quando a instalação termina.
4. Em macOS ou Linux, a partir da pasta extraída, executa `adb install -r FeuTimer-AndroidTV.apk` e depois `adb shell am start -W -n pt.feubjj.timer/.MainActivity`.

[Android SDK Platform-Tools](https://developer.android.com/tools/releases/platform-tools) · [Ligar um dispositivo com ADB](https://developer.android.com/tools/adb)

## Atualizações e assinaturas

A v1.4.0 usa o mesmo identificador e o mesmo certificado das versões 1.3.5, 1.3.6, 1.3.7, 1.3.8 e 1.3.9 publicadas neste repositório. A atualização direta da v1.3.7 para a v1.3.8 foi testada num emulador Android TV. A v1.3.4 do ZIP original tem outro certificado. Se o Android indicar conflito de assinatura, interrompe a instalação e conserva a aplicação existente. Uma desinstalação pode apagar predefinições e resultados.

Na TV, **Definições → Atualizar** abre o APK da versão estável mais recente através do navegador. Abre o APK transferido, confirma a instalação no Android e escolhe **Abrir**. Em TVs sem navegador, usa a pen ou o instalador por ADB. O reinício automático do simulador é uma função do lançador de PC.

[Versões do FeuTimer](https://github.com/ryoken99/FeuTimer/releases)
