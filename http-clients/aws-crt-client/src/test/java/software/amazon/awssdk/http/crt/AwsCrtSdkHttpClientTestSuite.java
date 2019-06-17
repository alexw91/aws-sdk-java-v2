package software.amazon.awssdk.http.crt;

import static software.amazon.awssdk.utils.FunctionalUtils.invokeSafely;

import java.net.HttpURLConnection;
import software.amazon.awssdk.crt.io.ClientBootstrap;
import software.amazon.awssdk.crt.io.SocketOptions;
import software.amazon.awssdk.crt.io.TlsContext;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpClientTestSuite;

public class AwsCrtSdkHttpClientTestSuite /* extends SdkHttpClientTestSuite */ {
//    @Override
//    protected SdkHttpClient createSdkHttpClient(SdkHttpClientOptions options) {
//        return UrlConnectionHttpClient.create((uri) -> invokeSafely(() -> (HttpURLConnection) uri.toURL().openConnection()));
//        return AwsCrtAsyncHttpClient.builder()
//                .bootstrap(new ClientBootstrap(1))
//                .socketOptions(new SocketOptions())
//                .tlsContext(new TlsContext())
//                .build();
//    }
}
