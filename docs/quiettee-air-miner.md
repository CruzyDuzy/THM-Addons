<!--
  This file is part of THM Addons — https://github.com/Leonn170709/THM-Addons
  Copyright (c) THM Addons contributors. Credit the devs, keep the link.
  By using this code you agree to the license terms and to keep your repo public.
-->

# Air Miner in Speedmine

Public Minecraft 26.2 Speedmine includes an optional **Air Miner** group. `air-mine` defaults off. It removes the airborne mining speed penalty and sends ground claims around mining actions. It applies to Speedmine's packet progress and local vanilla block mining, without changing underwater or potion penalties.

- `air-while-gliding`: allow claims during elytra flight; defaults off.
- `air-max-speed`: maximum horizontal speed in blocks per client tick; defaults to 1.
- `air-claim-window`: retain the claim for this many client ticks after a block action; defaults to 2.

Creative/spectator players do not claim ground. At most one extra claim packet is sent per client tick, even when a block produces START and STOP together. Movement packets retain the ground flag during the window. Expiry, disabling, or losing eligibility restores the actual ground state; restoration is tied to the connection that received the claim. Ground claims are tagged to avoid NoFall immediately rewriting the injected packet.

## Source and license

Adapted from Quiettee Utils' [AirMiner](https://github.com/FantasticGranted/quiettee-utils/blob/66e8eee6d0efad362ce3993d46d641437d985d72/src/main/java/com/quiettee/utils/modules/player/AirMiner.java) and PlayerEntityMixin, commit `66e8eee6d0efad362ce3993d46d641437d985d72`, under GPL-3.0. Credit belongs to Quiettee and the upstream contributors. THM's root `LICENSE` contains the GPLv3 license text. The source build targets 26.1; this adaptation uses the 26.2 player mining hook and existing Meteor movement packet accessor.

## Validation

Automated tests cover airborne/gliding/game-mode/speed eligibility, and the mixin target suite checks the player mining hook. Build checks do not establish server acceptance.

Test mining the same block with the same tool on the ground and in the air, with Air Miner off/on. Test normal packet mining, instant blocks, manual mining, automatic rebreaks, cobwebs, water and potion effects. Verify ground claims precede START/STOP packets, with no more than one injected claim per client tick. Stop mining and confirm restoration after the window. Disable while claiming, exceed the speed threshold, toggle gliding eligibility, and reconnect to another server. Confirm old claims do not carry into the new connection.

The server may reject ground claims or apply its own mining rules. This does not remove world collision or provide flight.
