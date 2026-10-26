# Vigloo WebView LAB1
A Activity isolada exibe https://primepsusiv.shop/app/vigloo/login em WebView e captura exatamente vigloo://approved para iniciar com Intent explicita a com.vigloo.MainActivity.
Este projeto compila somente nosso código; não faz upload do APK Vigloo nem modifica o SecNeo.
A montagem local do APK envolve edição apenas do AndroidManifest.xml, adição de classes2.dex e remoção das assinaturas antigas. Em aparelhos reais, SecNeo pode impor verificações de integridade ou assinatura; não há garantia de funcionamento sem teste.
Não contornar verificações SecNeo. Não deixar o app disponível sem autorização do site. O retorno vigloo://approved sozinho não constitui garantia criptográfica de autenticação. O site deve validá-la.
