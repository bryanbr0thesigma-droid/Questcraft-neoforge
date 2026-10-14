package questcraft.legacy;

import java.applet.AppletContext;
import java.applet.AppletStub;
import java.awt.Panel;
import java.net.URL;

/**
 * What the game gets in place of {@code java.applet.Applet} (see {@link Transformer}), which a headless JVM cannot
 * make. From 1.0 to 1.5 the game's main() runs it as an applet in a stand-in browser (its own AppletStub), and the
 * applet reads its parameters (user name, session) from there.
 */
@SuppressWarnings({"deprecation", "removal"})
public class HeadlessApplet extends Panel {
    private AppletStub stub;

    public HeadlessApplet() {
    }

    @Override
    protected void addImpl(java.awt.Component component, Object constraints, int index) {
        super.addImpl(component, constraints, index);
        HeadlessFrame.added(component);
    }

    public final void setStub(AppletStub stub) {
        this.stub = stub;
    }

    public boolean isActive() {
        return stub == null || stub.isActive();
    }

    public URL getDocumentBase() {
        return stub == null ? null : stub.getDocumentBase();
    }

    public URL getCodeBase() {
        return stub == null ? null : stub.getCodeBase();
    }

    public String getParameter(String name) {
        return stub == null ? null : stub.getParameter(name);
    }

    public AppletContext getAppletContext() {
        return stub == null ? null : stub.getAppletContext();
    }

    public void showStatus(String message) {
    }

    public String getAppletInfo() {
        return null;
    }

    public String[][] getParameterInfo() {
        return null;
    }

    public void init() {
    }

    public void start() {
    }

    public void stop() {
    }

    public void destroy() {
    }
}
