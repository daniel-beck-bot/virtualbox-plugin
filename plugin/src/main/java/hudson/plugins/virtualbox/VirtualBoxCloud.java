package hudson.plugins.virtualbox;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.Cloud;
import hudson.slaves.NodeProvisioner;
import hudson.util.FormValidation;
import hudson.util.Secret;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;

/**
 * {@link Cloud} implementation for VirtualBox.
 *
 * @author Evgeny Mandrikov
 */
public class VirtualBoxCloud extends Cloud {

  private static final Logger LOG = Logger.getLogger(VirtualBoxCloud.class.getName());

  private final String url;
  private final String username;
  private final Secret password;
  private final Integer activeMachineLimit;
  private final Semaphore activeMachines;

  /**
   * Lazily computed list of virtual machines from this host.
   */
  private transient List<VirtualBoxMachine> virtualBoxMachines = null;

  @DataBoundConstructor
  public VirtualBoxCloud(String displayName, String url, String username, Secret password, Integer activeMachineLimit) {
    super(displayName);
    this.url = url;
    this.username = username;
    this.password = password;
    this.activeMachineLimit = activeMachineLimit;
    if (this.activeMachineLimit == 0 || this.activeMachineLimit == null) {
      this.activeMachines = null;
    }
    else {
      this.activeMachines = new Semaphore(this.activeMachineLimit);
    }
  }

  @Override
  public Collection<NodeProvisioner.PlannedNode> provision(Label label, int excessWorkload) {
    return Collections.emptyList();
  }

  @Override
  public boolean canProvision(Label label) {
    return false;
  }


  public synchronized List<VirtualBoxMachine> refreshVirtualMachinesList() {
    virtualBoxMachines = VirtualBoxUtils.getMachines(this, new VirtualBoxSystemLog(LOG, "[VirtualBox] "));
    return virtualBoxMachines;
  }

  public synchronized VirtualBoxMachine getVirtualMachine(String virtualMachineName) {
    if (null == virtualBoxMachines) {
      refreshVirtualMachinesList();
    }
    for (VirtualBoxMachine machine: virtualBoxMachines) {
      if (virtualMachineName.equals(machine.getName())) {
        return machine;
      }
    }
    return null;
  }

  @Extension
  public static class DescriptorImpl extends Descriptor<Cloud> {
    @Override
    public String getDisplayName() {
      return Messages.VirtualBoxHost_displayName();
    }

    /**
     * For UI.
     */
    @SuppressWarnings({"UnusedDeclaration", "JavaDoc"})
    public FormValidation doTestConnection(
        @QueryParameter String url,
        @QueryParameter String username,
        @QueryParameter Secret password,
        @QueryParameter Integer activeMachineLimit
    ) {
      LOG.log(Level.INFO, "Testing connection to {0} with username {1}", new Object[]{url, username});
      try {
        VirtualBoxUtils.getMachines(new VirtualBoxCloud("testConnection", url, username, password, activeMachineLimit),
                new VirtualBoxSystemLog(LOG, "[VirtualBox] "));
        return FormValidation.ok(Messages.VirtualBoxHost_success());
      } catch (Throwable e) {
        return FormValidation.error(e.getMessage());
      }
    }
  }

  public String getUrl() {
    return url;
  }

  public String getUsername() {
    return username;
  }

  public Secret getPassword() { return password; }

  public void incrementActiveMachines() throws InterruptedException {
    if (activeMachines != null) {
      this.activeMachines.acquire();
    }
    else { return; }
  }

  public void decrementActiveMachines() {
    if (activeMachines != null) {
      this.activeMachines.release();
    }
    else { return; }
  }

  @Override
  public String toString() {
    final StringBuffer sb = new StringBuffer();
    sb.append("VirtualBoxHost");
    sb.append("{url='").append(url).append('\'');
    sb.append(", username='").append(username).append('\'');
    sb.append('}');
    return sb.toString();
  }
}
