package com.shiny.joypadmod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.settings.KeyBinding;

import org.lwjgl.input.Controllers;

import com.shiny.joypadmod.ControllerSettings.JoyBindingEnum;
import com.shiny.joypadmod.helpers.LogHelper;
import com.shiny.joypadmod.minecraftExtensions.JoypadConfigMenu;

import cpw.mods.fml.common.ObfuscationReflectionHelper;

public class GameRenderHandler
{
	private static Minecraft mc = Minecraft.getMinecraft();
	public static int reticalColor = 0xFFFFFFFF;
	public static VirtualMouse joypadMouse = new VirtualMouse();
	public static boolean allowMouseMenu = false;
	private static long lastInGuiTick = 0;
	private static long lastInGameTick = 0;

	public static void HandlePreRender()
	{
		if (mc.currentScreen != null && !ControllerSettings.isSuspended())
		{
			try
			{
				if (mc.currentScreen instanceof GuiControls)
				{
					if (!allowMouseMenu)
					{
						ReplaceControlScreen((GuiControls) mc.currentScreen);
					}
				}
				else if (!(mc.currentScreen instanceof JoypadConfigMenu))
				{
					allowMouseMenu = false;
				}

				if (InGuiCheckNeeded())
					HandleGuiMousePreRender();
			}
			catch (Exception ex)
			{
				LogHelper.Fatal("Joypad mod unhandled exception caught! " + ex.toString());
			}
		}
	}

	public static void HandlePostRender()
	{
		if (ControllerSettings.isSuspended())
			return;

		try
		{
			if (InGuiCheckNeeded())
			{
				HandleGuiMousePostRender();
				lastInGuiTick = Minecraft.getSystemTime();
			}

			if (InGameCheckNeeded())
			{
				HandleJoystickInGame();
				lastInGameTick = Minecraft.getSystemTime();
			}
		}
		catch (Exception ex)
		{
			LogHelper.Fatal("Joypad mod unhandled exception caught! " + ex.toString());
		}

	}

	private static void HandleGuiMousePreRender()
	{
		if (mc.currentScreen == null || !JoypadMod.controllerSettings.isInputEnabled())
			return;

		// fixes issue with transitioning from inGame to Gui movement continuing
		if (Minecraft.getSystemTime() - lastInGameTick < 100)
			KeyBinding.unPressAllKeys();

		// update mouse coordinates
		joypadMouse.getX();
		joypadMouse.getY();

		while (Controllers.next() && mc.currentScreen != null)
		{
			// ignore controller events in the milliseconds following in GAME
			// controlling
			if (Minecraft.getSystemTime() - lastInGameTick < 200)
				continue;

			if (mc.currentScreen instanceof GuiContainer)
			{
				if (ControllerSettings.get(JoyBindingEnum.joyBindShiftClick).wasPressed())
				{
					System.out.println("Shift Click");
					joypadMouse.hack_shiftKey(true);
					joypadMouse.leftButtonDown();
					continue;
				}
				joypadMouse.hack_shiftKey(ControllerSettings.get(JoyBindingEnum.joyBindSneak).isPressed());
			}

			if (joypadMouse.leftButtonHeld && !ControllerSettings.get(JoyBindingEnum.joyBindAttack).isPressed())
				joypadMouse.leftButtonUp();

			if (joypadMouse.rightButtonHeld && !ControllerSettings.get(JoyBindingEnum.joyBindUseItem).isPressed())
				joypadMouse.rightButtonUp();

			if (ControllerSettings.get(JoyBindingEnum.joyBindInventory).wasPressed())
			{
				LogHelper.Debug("Inventory control pressed");

				if (mc.thePlayer != null)
					mc.thePlayer.closeScreen();
				else
				{
					// backup
					JoypadMod.obfuscationHelper.DisplayGuiScreen(null);
				}
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindAttack).wasPressed())
			{
				joypadMouse.leftButtonDown();

			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindUseItem).wasPressed())
			{
				joypadMouse.rightButtonDown();

			}
		}

		if (mc.currentScreen != null)
		{

			// This call here re-points the mouse position that Minecraft picks
			// up to determine if it should do the Hover over button effect.
			joypadMouse.hack_mouseXY(joypadMouse.mcX, joypadMouse.mcY);

			if (joypadMouse.leftButtonHeld || joypadMouse.rightButtonHeld)
			{
				joypadMouse.gui_mouseDrag(joypadMouse.x, joypadMouse.y);
				VirtualMouse.hack_mouseButton(joypadMouse.leftButtonHeld ? 0 : 1);
			}
		}
		else
		{
			joypadMouse.UnpressButtons();
		}
	}

	private static void HandleGuiMousePostRender()
	{

		if (mc.currentScreen == null || !JoypadMod.controllerSettings.isInputEnabled())
			return;

		int x = joypadMouse.x;
		int y = joypadMouse.y;

		Gui.drawRect(x - 3, y, x + 4, y + 1, reticalColor);
		Gui.drawRect(x, y - 3, x + 1, y + 4, reticalColor);
	}

	private static int attackKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindAttack);
	private static int useKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindUseItem);
	private static int inventoryKeyCode = JoypadMod.obfuscationHelper
			.KeyBindCodeHelper(mc.gameSettings.keyBindInventory);

	// does this have to be run in post render or pre? maybe doesn't
	// matter...but be wary if changing it around
	private static void HandleJoystickInGame()
	{
		if (ControllerSettings.get(JoyBindingEnum.joyBindAttack).isPressed())
		{
			// this call ensures that you can break blocks in non-creative!
			mc.inGameHasFocus = true;
		}

		while (Controllers.next())
		{
			// ignore controller events in the milliseconds following in GUI
			// controlling
			if (Minecraft.getSystemTime() - lastInGuiTick < 100)
				continue;

			if (ControllerSettings.get(JoyBindingEnum.joyBindAttack).wasPressed())
			{
				System.out.println("Initiating attack ontick");
				KeyBinding.onTick(attackKeyCode);
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindUseItem).wasPressed())
			{
				// this call is probably unnecessary but keeping here in case it
				// solves some edge case
				System.out.println("Initiating use ontick");
				KeyBinding.onTick(useKeyCode);
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindInventory).wasPressed())
			{
				LogHelper.Debug("Inventory control pressed");
				KeyBinding.onTick(inventoryKeyCode);
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindNextItem).wasPressed())
			{
				LogHelper.Debug("NextItem pressed");
				mc.thePlayer.inventory.changeCurrentItem(-1);
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindPrevItem).wasPressed())
			{
				LogHelper.Debug("PrevItem pressed");
				mc.thePlayer.inventory.changeCurrentItem(1);
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindMenu).wasPressed())
			{
				if (mc.currentScreen != null)
				{
					JoypadMod.obfuscationHelper.DisplayGuiScreen(null);
				}
				else
				{
					mc.displayInGameMenu();
				}
			}
			else if (ControllerSettings.get(JoyBindingEnum.joyBindDrop).wasPressed())
			{
				// TODO: add option to drop more than 1 item
				mc.thePlayer.dropOneItem(true);
			}

			KeyBinding.setKeyBindState(useKeyCode, ControllerSettings.get(JoyBindingEnum.joyBindUseItem).isPressed());
			KeyBinding.setKeyBindState(attackKeyCode, ControllerSettings.get(JoyBindingEnum.joyBindAttack).isPressed());

			// UpdateFocusState();
			HandlePlayerMovement();
		}

		// Read joypad movement
		VirtualMouse.updateCameraAxisReading();
		mc.thePlayer.setAngles(VirtualMouse.deltaX, VirtualMouse.deltaY);
	}

	// TODO this is almost getting big enough to warrant its own class
	private static int forwardKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindForward);
	private static int backKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindBack);
	private static int leftKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindLeft);
	private static int rightKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindRight);
	private static int jumpKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindJump);
	private static int sneakKeyCode = JoypadMod.obfuscationHelper.KeyBindCodeHelper(mc.gameSettings.keyBindSneak);

	private static void HandlePlayerMovement()
	{
		if (JoypadMod.controllerSettings.isInputEnabled() && ControllerSettings.joystick != null)
		{
			float xPlus = ControllerSettings.get(JoyBindingEnum.joyMovementXplus).getAnalogReading();
			float xMinus = ControllerSettings.get(JoyBindingEnum.joyMovementXminus).getAnalogReading();
			float xAxisValue = Math.abs(xPlus) > Math.abs(xMinus) ? xPlus : xMinus;

			float yPlus = ControllerSettings.get(JoyBindingEnum.joyMovementYplus).getAnalogReading();
			float yMinus = ControllerSettings.get(JoyBindingEnum.joyMovementYminus).getAnalogReading();
			float yAxisValue = Math.abs(yPlus) > Math.abs(yMinus) ? yPlus : yMinus;

			KeyBinding.setKeyBindState(forwardKeyCode, yAxisValue < 0);
			KeyBinding.setKeyBindState(backKeyCode, yAxisValue > 0);
			KeyBinding.setKeyBindState(leftKeyCode, xAxisValue < 0);
			KeyBinding.setKeyBindState(rightKeyCode, xAxisValue > 0);
			KeyBinding.setKeyBindState(sneakKeyCode, ControllerSettings.get(JoyBindingEnum.joyBindSneak).isPressed());
			KeyBinding.setKeyBindState(jumpKeyCode, ControllerSettings.get(JoyBindingEnum.joyBindJump).isPressed());
		}
	}

	private static void ReplaceControlScreen(GuiControls gui)
	{
		if (!(mc.currentScreen instanceof JoypadConfigMenu))
		{
			try
			{
				LogHelper.Debug("Replacing control screen");
				String[] names = JoypadMod.obfuscationHelper.GetMinecraftVarNames("parentScreen");
				GuiScreen parent = ObfuscationReflectionHelper.getPrivateValue(GuiControls.class, (GuiControls) gui,
						names[0], names[1]);
				JoypadMod.obfuscationHelper.DisplayGuiScreen(new JoypadConfigMenu(parent, gui));
			}
			catch (Exception ex)
			{
				LogHelper.Debug("Failed to get parent of options gui.  aborting");
				return;
			}
		}
	}

	public static boolean InGameCheckNeeded()
	{
		if (!CheckIfModEnabled() || mc.currentScreen != null || mc.thePlayer == null)
		{
			return false;
		}

		return true;
	}

	public static boolean InGuiCheckNeeded()
	{
		if (!CheckIfModEnabled() || mc.currentScreen == null)
		{
			return false;
		}

		return true;
	}

	public static boolean CheckIfModEnabled()
	{
		if (mc == null || !JoypadMod.controllerSettings.isInputEnabled() || ControllerSettings.joystick == null)
		{
			return false;
		}

		return true;
	}
}
