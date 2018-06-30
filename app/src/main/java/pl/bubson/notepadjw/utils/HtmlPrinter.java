package pl.bubson.notepadjw.utils;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Build;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.util.Log;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import pl.bubson.notepadjw.R;

/**
 * Created by Kuba on 2018-06-24.
 */

public class HtmlPrinter {
    private WebView mWebView;
    private Context context;

    public HtmlPrinter(Context context) {
        this.context = context;
    }

    public void print(String htmlDocument) {
        // Create a WebView object specifically for printing
        WebView webView = new WebView(context);

        webView.setWebViewClient(new WebViewClient() {

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }

            @TargetApi(Build.VERSION_CODES.N)
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                Log.i("Html Printer", "onPageStarted");
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                Log.i("Html Printer", "page finished loading " + url);
                createWebPrintJob(view);
                mWebView = null;
            }

        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                Log.i("Html Printer", "onProgressChanged: " + newProgress);
            }
        });

        // Example of HTML document :
        // "<html><body><h1>Test Content</h1><p>Testing, testing, testing.</p></body></html>";
        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null);

        // Keep a reference to WebView object until you pass the PrintDocumentAdapter
        // to the PrintManager
        mWebView = webView;
    }

    private void createWebPrintJob(WebView webView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // Get a PrintManager instance
            PrintManager printManager = (PrintManager) context.getSystemService(Context.PRINT_SERVICE);

            // Create a print job with name and adapter instance
            String jobName = context.getResources().getString(R.string.app_name) + " " + context.getResources().getString(R.string.note);

            // Get a print adapter instance
            PrintDocumentAdapter printAdapter = webView.createPrintDocumentAdapter();

            printManager.print(jobName, printAdapter, new PrintAttributes.Builder().build());
        } else {
            Toast.makeText(context, R.string.unsuccessful, Toast.LENGTH_SHORT).show();
        }
    }
}
