package love.shirokasoke.aggressivepatch;

import net.minecraftforge.client.ClientCommandHandler;

import cpw.mods.fml.common.event.FMLInitializationEvent;
import love.shirokasoke.aggressivepatch.commands.Client;

public class ClientProxy extends CommonProxy {

    // Override CommonProxy methods here, if you want a different behaviour on the client (e.g. registering renders).
    // Don't forget to call the super methods as well.

    @Override
    public void init(FMLInitializationEvent event) {
        super.init(event);
        // Client commands have no dedicated FML event, register them on Forge's handler singleton directly.
        ClientCommandHandler.instance.registerCommand(new Client());
    }
}
