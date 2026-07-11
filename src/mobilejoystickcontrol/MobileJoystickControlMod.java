package mobilejoystickcontrol;

import arc.Core;
import arc.Events;
import arc.graphics.Color;
import arc.graphics.g2d.Draw;
import arc.graphics.g2d.Fill;
import arc.graphics.g2d.Lines;
import arc.graphics.g2d.TextureRegion;
import arc.input.KeyCode;
import arc.math.Angles;
import arc.math.Mathf;
import arc.math.geom.Vec2;
import arc.scene.Element;
import arc.scene.event.InputEvent;
import arc.scene.event.InputListener;
import arc.scene.event.Touchable;
import arc.scene.ui.Label;
import arc.scene.ui.Slider;
import arc.scene.ui.layout.Scl;
import arc.scene.ui.layout.Table;
import arc.util.Align;
import arc.util.Time;
import mindustry.game.EventType.ClientLoadEvent;
import mindustry.game.EventType.ResetEvent;
import mindustry.game.EventType.Trigger;
import mindustry.game.EventType.WorldLoadEvent;
import mindustry.gen.Mechc;
import mindustry.gen.Unit;
import mindustry.graphics.Pal;
import mindustry.input.DesktopInput;
import mindustry.input.MobileInput;
import mindustry.mod.Mod;
import mindustry.mod.Mods;
import mindustry.ui.Fonts;
import mindustry.ui.Styles;
import mindustry.ui.dialogs.SettingsMenuDialog;

import static mindustry.Vars.*;

public class MobileJoystickControlMod extends Mod{
    private static final String overlayName = "mjc-overlay";
    private static final String editBarName = "mjc-edit-bar";

    private static final String keyEnabled = "mjc-enabled";
    private static final String keyEditLayout = "mjc-edit-layout";
    private static final String keyDualStickMode = "mjc-dual-stick-mode";
    private static final String keyDebugDesktop = "mjc-debug-desktop";
    private static final String keyDeadzone = "mjc-deadzone";
    private static final String keyHudAlpha = "mjc-hud-alpha";
    private static final String keyAimDistance = "mjc-aim-distance";

    private static final String keyLeftX = "mjc-left-x";
    private static final String keyLeftY = "mjc-left-y";
    private static final String keyLeftSize = "mjc-left-size";

    private static final String keyRightX = "mjc-right-x";
    private static final String keyRightY = "mjc-right-y";
    private static final String keyRightSize = "mjc-right-size";

    private static final String keyBoostX = "mjc-boost-x";
    private static final String keyBoostY = "mjc-boost-y";
    private static final String keyBoostSize = "mjc-boost-size";

    private static final String keyFireX = "mjc-fire-x";
    private static final String keyFireY = "mjc-fire-y";
    private static final String keyFireSize = "mjc-fire-size";

    private static final int defLeftX = 18;
    private static final int defLeftY = 22;
    private static final int defLeftSize = 180;

    private static final int defRightX = 82;
    private static final int defRightY = 22;
    private static final int defRightSize = 180;

    private static final int defBoostX = 90;
    private static final int defBoostY = 42;
    private static final int defBoostSize = 64;

    private static final int defFireX = 74;
    private static final int defFireY = 42;
    private static final int defFireSize = 64;

    private JoystickOverlay overlay;
    private JoystickMobileInput overrideInput;
    private boolean settingsRegistered;
    private Table editBar;
    private Label editBarLabel;
    private Slider editBarSlider;
    private boolean syncingEditBar;
    private boolean hudVisibilityInitialized;
    private boolean lastHudShown;
    private boolean uiRefreshQueued;

    public MobileJoystickControlMod(){
        setupDefaults();

        Events.on(ClientLoadEvent.class, e -> {
            if(headless) return;
            ensureSettingsCategory();
            ensureInputOverride();
            if(runtimeEnabled()){
                requestUiRefresh(10f);
            }
        });

        Events.on(WorldLoadEvent.class, e -> {
            if(headless || !runtimeEnabled()) return;
            requestUiRefresh(10f);
        });

        Events.on(ResetEvent.class, e -> {
            if(overlay != null) overlay.resetTouchState(true);
            requestUiRefresh(1f);
        });

        Events.run(Trigger.update, () -> {
            if(headless) return;
            ensureInputOverride();
            if(runtimeEnabled()){
                ensureOverlayAttached();
                ensureEditBarAttached();
                updateHudVisibility();
            }
            syncEditBarState();
        });
    }

    private void setupDefaults(){
        Core.settings.defaults(keyEnabled, true);
        Core.settings.defaults(keyEditLayout, false);
        Core.settings.defaults(keyDualStickMode, true);
        Core.settings.defaults(keyDebugDesktop, false);
        Core.settings.defaults(keyDeadzone, 18);
        Core.settings.defaults(keyHudAlpha, 90);
        Core.settings.defaults(keyAimDistance, 170);

        Core.settings.defaults(keyLeftX, defLeftX);
        Core.settings.defaults(keyLeftY, defLeftY);
        Core.settings.defaults(keyLeftSize, defLeftSize);

        Core.settings.defaults(keyRightX, defRightX);
        Core.settings.defaults(keyRightY, defRightY);
        Core.settings.defaults(keyRightSize, defRightSize);

        Core.settings.defaults(keyBoostX, defBoostX);
        Core.settings.defaults(keyBoostY, defBoostY);
        Core.settings.defaults(keyBoostSize, defBoostSize);

        Core.settings.defaults(keyFireX, defFireX);
        Core.settings.defaults(keyFireY, defFireY);
        Core.settings.defaults(keyFireSize, defFireSize);
    }

    private void ensureSettingsCategory(){
        if(settingsRegistered || ui == null || ui.settings == null) return;

        ui.settings.addCategory("@mjc.category", this::buildSettings);
        settingsRegistered = true;
    }

    private void buildSettings(SettingsMenuDialog.SettingsTable table){
        table.checkPref(keyEnabled, true);
        table.checkPref(keyEditLayout, false);
        table.checkPref(keyDualStickMode, true);
        table.checkPref(keyDebugDesktop, false);
        table.sliderPref(keyDeadzone, 18, 0, 45, 1, v -> v + "%");
        table.sliderPref(keyHudAlpha, 90, 20, 100, 1, v -> v + "%");
        table.sliderPref(keyAimDistance, 170, 80, 480, 10, v -> v + "u");

        addSectionTitle(table, "@mjc.section.left");
        table.sliderPref(keyLeftX, defLeftX, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyLeftY, defLeftY, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyLeftSize, defLeftSize, 80, 280, 2, v -> v + "px");

        addSectionTitle(table, "@mjc.section.right");
        table.sliderPref(keyRightX, defRightX, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyRightY, defRightY, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyRightSize, defRightSize, 80, 280, 2, v -> v + "px");

        addSectionTitle(table, "@mjc.section.boost");
        table.sliderPref(keyBoostX, defBoostX, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyBoostY, defBoostY, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyBoostSize, defBoostSize, 40, 160, 2, v -> v + "px");

        addSectionTitle(table, "@mjc.section.fire");
        table.sliderPref(keyFireX, defFireX, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyFireY, defFireY, 0, 100, 1, v -> v + "%");
        table.sliderPref(keyFireSize, defFireSize, 40, 160, 2, v -> v + "px");
    }

    private void addSectionTitle(Table table, String text){
        table.row();
        table.image().growX().height(2f).color(Pal.accent).padTop(6f).padBottom(4f);
        table.row();
        table.add(text).left().color(Color.lightGray).padBottom(4f);
    }

    private void ensureInputOverride(){
        if(control == null || control.input == null) return;

        if(control.input instanceof JoystickMobileInput){
            overrideInput = (JoystickMobileInput)control.input;
            if(!mobile){
                control.setInput(new DesktopInput());
                overrideInput = null;
            }
            return;
        }

        if(control.input instanceof JoystickDesktopInput){
            if(!desktopDebugEnabled()){
                control.setInput(new DesktopInput());
            }
            return;
        }

        // Replace vanilla inputs only. If another mod already replaced input, keep compatibility.
        if(mobile){
            if(control.input instanceof MobileInput && control.input.getClass() == MobileInput.class){
                JoystickMobileInput input = new JoystickMobileInput(this);
                control.setInput(input);
                overrideInput = input;
            }
            return;
        }

        if(desktopDebugEnabled() && control.input instanceof DesktopInput && control.input.getClass() == DesktopInput.class){
            control.setInput(new JoystickDesktopInput(this));
        }
    }

    private void ensureOverlayAttached(){
        if(ui == null || ui.hudGroup == null) return;
        if(overlay != null && overlay.parent == ui.hudGroup) return;

        Element existing = ui.hudGroup.find(overlayName);
        if(existing instanceof JoystickOverlay && existing.parent == ui.hudGroup){
            overlay = (JoystickOverlay)existing;
            return;
        }

        if(overlay != null){
            overlay.resetTouchState(true);
            overlay.remove();
            overlay = null;
        }

        if(existing != null){
            existing.remove();
        }

        overlay = new JoystickOverlay(this);
        overlay.name = overlayName;
        overlay.setFillParent(true);
        overlay.touchable = Touchable.enabled;
        ui.hudGroup.addChild(overlay);
    }

    private void ensureEditBarAttached(){
        if(ui == null || ui.hudGroup == null) return;
        if(editBar != null && editBar.parent == ui.hudGroup) return;

        Element existing = ui.hudGroup.find(editBarName);
        if(editBar != null){
            editBar.remove();
            editBar = null;
            editBarLabel = null;
            editBarSlider = null;
        }

        if(existing != null){
            existing.remove();
        }

        editBar = new Table();
        editBar.name = editBarName;
        editBar.setFillParent(true);
        editBar.top();
        editBar.touchable = Touchable.childrenOnly;

        Table content = new Table(Styles.black6);
        content.margin(8f);

        editBarLabel = new Label("", Styles.outlineLabel);
        editBarSlider = new Slider(25f, 400f, 1f, false);
        editBarSlider.changed(() -> {
            if(syncingEditBar || overlay == null) return;
            overlay.setSelectedScalePercent(Mathf.round(editBarSlider.getValue()));
            syncEditBarState();
        });

        content.add(editBarLabel).padRight(12f).minWidth(160f).left();
        content.add(editBarSlider).width(320f).growX();
        editBar.add(content).padTop(10f).width(520f).growX().top();
        ui.hudGroup.addChild(editBar);
    }

    private void updateHudVisibility(){
        boolean hudShown = ui != null && ui.hudfrag != null && ui.hudfrag.shown;
        if(!hudVisibilityInitialized){
            hudVisibilityInitialized = true;
            lastHudShown = hudShown;
            return;
        }

        if(hudShown == lastHudShown) return;
        lastHudShown = hudShown;

        if(overlay != null){
            overlay.resetTouchState(true);
        }

        if(hudShown){
            requestUiRefresh(1f);
        }
    }

    private void requestUiRefresh(float delay){
        if(headless || uiRefreshQueued) return;
        uiRefreshQueued = true;
        Time.runTask(delay, () -> {
            uiRefreshQueued = false;
            if(!runtimeEnabled()) return;
            rebuildUi();
        });
    }

    private void rebuildUi(){
        if(overlay != null){
            overlay.resetTouchState(true);
            overlay.remove();
            overlay = null;
        }

        if(editBar != null){
            editBar.remove();
            editBar = null;
            editBarLabel = null;
            editBarSlider = null;
        }

        ensureOverlayAttached();
        ensureEditBarAttached();
    }

    private void syncEditBarState(){
        if(editBar == null) return;

        boolean show = runtimeEnabled()
            && shouldOverlayBeVisible()
            && overlay != null
            && Core.settings.getBool(keyEditLayout, false)
            && overlay.hasSelectedControl();

        editBar.visible = show;
        editBar.touchable = show ? Touchable.childrenOnly : Touchable.disabled;
        if(!show) return;

        syncingEditBar = true;
        editBarLabel.setText(Core.bundle.format("mjc.sizebar.label", overlay.selectedControlName(), overlay.getSelectedScalePercent()));
        editBarSlider.setValue(overlay.getSelectedScalePercent());
        syncingEditBar = false;
        editBar.toFront();
    }

    boolean shouldOverlayBeVisible(){
        if(!runtimeEnabled()) return false;
        if(!Core.settings.getBool(keyEnabled, true)) return false;
        if(state == null || !state.isGame()) return false;
        if(ui == null || ui.hudfrag == null || !ui.hudfrag.shown) return false;
        if(player == null) return false;
        return true;
    }

    private boolean desktopDebugEnabled(){
        return !mobile && Core.settings.getBool(keyDebugDesktop, false);
    }

    private boolean runtimeEnabled(){
        return !headless && (mobile || desktopDebugEnabled());
    }

    boolean shouldOverrideInput(){
        if(!shouldOverlayBeVisible()) return false;
        if(Core.settings.getBool(keyEditLayout, false)) return false;
        if(Core.scene == null) return false;
        if(Core.scene.hasDialog() || Core.scene.hasKeyboard()) return false;
        if(ui != null && ui.chatfrag != null && ui.chatfrag.shown()) return false;
        if(ui != null && ui.consolefrag != null && ui.consolefrag.shown()) return false;
        if(overlay == null) return false;
        return true;
    }

    private void fillState(JoystickState out){
        out.reset();
        if(overlay == null) return;
        overlay.fillState(out);
    }

    private boolean applyOverlayControl(Unit unit, JoystickState state, Vec2 move, Vec2 aim){
        if(!shouldOverrideInput() || unit == null || player == null || player.dead()){
            return false;
        }

        fillState(state);

        boolean moving = state.moveAmount > 0.0001f;
        if(!moving && state.wasMoving){
            unit.vel.setZero();
        }
        state.wasMoving = moving;

        float speed = unit.speed();
        move.set(state.moveX, state.moveY).scl(speed * state.moveAmount);
        unit.movePref(move);

        if(state.moveAmount > 0.0001f){
            Core.camera.position.set(unit);
        }

        boolean boostingRequested = state.boosting;
        boolean canBoost = unit.type.canBoost && !unit.type.flying;
        player.boosting = canBoost && boostingRequested;

        boolean boosted = (unit instanceof Mechc && unit.isFlying());
        if(state.shooting && !state.hasAim){
            state.hasAim = true;
            state.aimX = Angles.trnsx(unit.rotation, 1f);
            state.aimY = Angles.trnsy(unit.rotation, 1f);
        }

        boolean shooting = state.shooting && !boosted;
        player.shooting = shooting;

        if(state.hasAim){
            float aimDistance = Mathf.clamp(Core.settings.getInt(keyAimDistance, 170), 80, 480);
            float worldAimDistance = Math.max(aimDistance, unit.range() * 0.7f);

            aim.set(state.aimX, state.aimY);
            if(aim.isZero(0.0001f)){
                aim.trns(unit.rotation, 1f);
            }

            float aimX = unit.x + aim.x * worldAimDistance;
            float aimY = unit.y + aim.y * worldAimDistance;
            player.mouseX = aimX;
            player.mouseY = aimY;
            unit.aim(aimX, aimY);
        }

        if(shooting && unit.type.hasWeapons() && unit.type.faceTarget && state.hasAim){
            unit.lookAt(Mathf.angle(state.aimX, state.aimY));
        }else if(!move.isZero(0.0001f)){
            unit.lookAt(move.angle());
        }else{
            unit.lookAt(unit.prefRotation());
        }

        unit.controlWeapons(shooting, shooting);
        return true;
    }

    private static float applyDeadzone(float value, float deadzone){
        float d = Mathf.clamp(deadzone, 0f, 0.95f);
        float v = Mathf.clamp(value, 0f, 1f);
        if(v <= d) return 0f;
        return (v - d) / (1f - d);
    }

    private static final class JoystickState{
        float moveX;
        float moveY;
        float moveAmount;

        float aimX;
        float aimY;
        boolean hasAim;
        boolean shooting;
        boolean boosting;
        boolean wasMoving;

        void reset(){
            moveX = 0f;
            moveY = 0f;
            moveAmount = 0f;

            aimX = 0f;
            aimY = 0f;
            hasAim = false;
            shooting = false;
            boosting = false;
        }

        void resetMovementTracking(){
            wasMoving = false;
        }
    }

    private static final class JoystickMobileInput extends MobileInput{
        private final MobileJoystickControlMod mod;
        private final JoystickState state = new JoystickState();
        private final Vec2 move = new Vec2();
        private final Vec2 aim = new Vec2();

        JoystickMobileInput(MobileJoystickControlMod mod){
            this.mod = mod;
        }

        @Override
        protected void updateMovement(Unit unit){
            if(!mod.applyOverlayControl(unit, state, move, aim)){
                state.resetMovementTracking();
                super.updateMovement(unit);
            }
        }
    }

    private static final class JoystickDesktopInput extends DesktopInput{
        private final MobileJoystickControlMod mod;
        private final JoystickState state = new JoystickState();
        private final Vec2 move = new Vec2();
        private final Vec2 aim = new Vec2();

        JoystickDesktopInput(MobileJoystickControlMod mod){
            this.mod = mod;
        }

        @Override
        protected void updateMovement(Unit unit){
            if(!mod.applyOverlayControl(unit, state, move, aim)){
                state.resetMovementTracking();
                super.updateMovement(unit);
            }
        }
    }

    private static final class JoystickOverlay extends Element{
        private static final int none = 0;
        private static final int left = 1;
        private static final int right = 2;
        private static final int boost = 3;
        private static final int fire = 4;
        private static final int desktopPointer = -100;
        private static final float editDragThreshold = 10f;

        private final MobileJoystickControlMod mod;

        private final TextureRegion stickBase;
        private final TextureRegion stickKnob;
        private final TextureRegion boostButton;
        private final TextureRegion fireButton;

        private final Vec2 leftDir = new Vec2();
        private float leftAmount;

        private final Vec2 rightDir = new Vec2();
        private float rightAmount;

        private final Vec2 lastAim = new Vec2(1f, 0f);

        private boolean fireHeld;
        private boolean boostLatched;

        private int leftPointer = -1;
        private int rightPointer = -1;
        private int firePointer = -1;
        private int dragPointer = -1;
        private int dragTarget = none;
        private int selectedTarget = none;

        private float dragOffsetX;
        private float dragOffsetY;
        private float dragStartX;
        private float dragStartY;
        private boolean draggingEdit;
        private boolean desktopMouseDown;

        JoystickOverlay(MobileJoystickControlMod mod){
            this.mod = mod;

            stickBase = findRegion("mjc-stick-base");
            stickKnob = findRegion("mjc-stick-knob");
            boostButton = findRegion("mjc-button-boost");
            fireButton = findRegion("mjc-button-fire");

            addListener(new InputListener(){
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, KeyCode button){
                    if(!mod.shouldOverlayBeVisible()) return false;
                    return handlePress(pointer, event.stageX, event.stageY);
                }

                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer){
                    handleDrag(pointer, event.stageX, event.stageY);
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, KeyCode button){
                    handleRelease(pointer);
                }
            });
        }

        @Override
        public void act(float delta){
            super.act(delta);

            if(parent != null){
                setBounds(0f, 0f, parent.getWidth(), parent.getHeight());
            }

            boolean visible = mod.shouldOverlayBeVisible();
            this.visible = visible;
            touchable = visible ? Touchable.enabled : Touchable.disabled;

            if(visible){
                if(!Core.settings.getBool(keyEditLayout, false)){
                    toFront();
                }
                updateDesktopPointer();
            }

            if(!visible){
                resetTouchState(false);
            }
        }

        @Override
        public Element hit(float x, float y, boolean touchable){
            if(!visible) return null;
            if(touchable && this.touchable == Touchable.disabled) return null;

            if(insideControlArea(x, y)){
                return this;
            }

            if(Core.settings.getBool(keyEditLayout, false) && hasSelectedControl()){
                return this;
            }

            return null;
        }

        @Override
        public void draw(){
            if(!visible) return;

            float alpha = parentAlpha * Mathf.clamp(Core.settings.getInt(keyHudAlpha, 90) / 100f);
            if(alpha <= 0.001f) return;

            float leftX = centerX(keyLeftX, defLeftX);
            float leftY = centerY(keyLeftY, defLeftY);
            float leftSize = readSize(keyLeftSize, defLeftSize);

            float rightX = centerX(keyRightX, defRightX);
            float rightY = centerY(keyRightY, defRightY);
            float rightSize = readSize(keyRightSize, defRightSize);

            float boostX = centerX(keyBoostX, defBoostX);
            float boostY = centerY(keyBoostY, defBoostY);
            float boostSize = readSize(keyBoostSize, defBoostSize);

            float fireX = centerX(keyFireX, defFireX);
            float fireY = centerY(keyFireY, defFireY);
            float fireSize = readSize(keyFireSize, defFireSize);

            boolean editing = Core.settings.getBool(keyEditLayout, false);

            Draw.z(1000f);

            drawStick(leftX, leftY, leftSize, leftDir, leftAmount, alpha, true, editing);
            if(showRightStick()){
                drawStick(rightX, rightY, rightSize, rightDir, rightAmount, alpha, false, editing);
            }

            drawButton(boostButton, boostX, boostY, boostSize, alpha, boostLatched, editing, new Color(0.58f, 0.84f, 1f, 1f));
            if(showFireButton()){
                drawButton(fireButton, fireX, fireY, fireSize, alpha, fireHeld, editing, new Color(1f, 0.58f, 0.58f, 1f));
            }

            if(editing){
                Draw.color(Color.orange, alpha);
                Fonts.outline.draw(Core.bundle.get("mjc.layout.editing"), getWidth() * 0.5f, getHeight() * 0.90f, Align.center);
                Fonts.outline.draw(Core.bundle.get("mjc.layout.hint"), getWidth() * 0.5f, getHeight() * 0.85f, Align.center);
            }

            Draw.reset();
        }

        void fillState(JoystickState out){
            out.reset();
            if(!visible) return;

            float deadzone = Mathf.clamp(Core.settings.getInt(keyDeadzone, 18) / 100f, 0f, 0.95f);

            float moveAmount = applyDeadzone(leftAmount, deadzone);
            if(moveAmount > 0f){
                out.moveX = leftDir.x;
                out.moveY = leftDir.y;
                out.moveAmount = moveAmount;
                lastAim.set(leftDir);
            }

            if(showRightStick()){
                float shootAmount = applyDeadzone(rightAmount, deadzone);
                if(shootAmount > 0f){
                    out.shooting = true;
                    out.hasAim = true;
                    out.aimX = rightDir.x;
                    out.aimY = rightDir.y;
                    lastAim.set(rightDir);
                }
            }

            if(fireHeld){
                out.shooting = true;
                if(!out.hasAim && leftAmount > 0.001f){
                    out.hasAim = true;
                    out.aimX = leftDir.x;
                    out.aimY = leftDir.y;
                    lastAim.set(leftDir);
                }else if(!out.hasAim && !lastAim.isZero(0.001f)){
                    out.hasAim = true;
                    out.aimX = lastAim.x;
                    out.aimY = lastAim.y;
                }else if(!out.hasAim && player != null && player.unit() != null){
                    out.hasAim = true;
                    out.aimX = Angles.trnsx(player.unit().rotation, 1f);
                    out.aimY = Angles.trnsy(player.unit().rotation, 1f);
                }
            }

            out.boosting = boostLatched;
        }

        void resetTouchState(boolean clearBoost){
            leftPointer = -1;
            rightPointer = -1;
            firePointer = -1;
            dragPointer = -1;
            dragTarget = none;
            selectedTarget = none;
            fireHeld = false;
            draggingEdit = false;
            desktopMouseDown = false;

            leftAmount = 0f;
            rightAmount = 0f;
            leftDir.setZero();
            rightDir.setZero();

            if(clearBoost){
                boostLatched = false;
            }
        }

        private boolean touchDownEdit(int pointer, float sx, float sy){
            int target = pickDragTarget(sx, sy);
            if(target == none){
                boolean hadSelection = hasSelectedControl() || dragTarget != none;
                clearEditSelection();
                return hadSelection;
            }

            selectedTarget = target;
            dragPointer = pointer;
            dragTarget = target;
            draggingEdit = false;
            dragStartX = sx;
            dragStartY = sy;

            float cx = controlX(target);
            float cy = controlY(target);
            dragOffsetX = cx - sx;
            dragOffsetY = cy - sy;
            return true;
        }

        private void clearEditSelection(){
            dragPointer = -1;
            dragTarget = none;
            selectedTarget = none;
            draggingEdit = false;
        }

        private void touchDraggedEdit(int pointer, float sx, float sy){
            if(pointer != dragPointer || dragTarget == none) return;

            if(!draggingEdit){
                if(Mathf.dst2(sx, sy, dragStartX, dragStartY) < editDragThreshold * editDragThreshold) return;
                draggingEdit = true;
            }

            float size = controlSize(dragTarget);
            float nx = sx + dragOffsetX;
            float ny = sy + dragOffsetY;

            nx = Mathf.clamp(nx, size / 2f, Math.max(size / 2f, getWidth() - size / 2f));
            ny = Mathf.clamp(ny, size / 2f, Math.max(size / 2f, getHeight() - size / 2f));

            int px = Mathf.round(nx / Math.max(getWidth(), 1f) * 100f);
            int py = Mathf.round(ny / Math.max(getHeight(), 1f) * 100f);
            px = Mathf.clamp(px, 0, 100);
            py = Mathf.clamp(py, 0, 100);

            switch(dragTarget){
                case left:
                    Core.settings.put(keyLeftX, px);
                    Core.settings.put(keyLeftY, py);
                    break;
                case right:
                    Core.settings.put(keyRightX, px);
                    Core.settings.put(keyRightY, py);
                    break;
                case boost:
                    Core.settings.put(keyBoostX, px);
                    Core.settings.put(keyBoostY, py);
                    break;
                case fire:
                    Core.settings.put(keyFireX, px);
                    Core.settings.put(keyFireY, py);
                    break;
                default:
                    break;
            }
        }

        private boolean handlePress(int pointer, float sx, float sy){
            if(Core.settings.getBool(keyEditLayout, false)){
                return touchDownEdit(pointer, sx, sy);
            }

            if(leftPointer == -1 && insideLeftStick(sx, sy)){
                leftPointer = pointer;
                updateStick(left, sx, sy);
                return true;
            }

            if(showRightStick() && rightPointer == -1 && insideRightStick(sx, sy)){
                rightPointer = pointer;
                updateStick(right, sx, sy);
                return true;
            }

            if(showFireButton() && firePointer == -1 && insideFireButton(sx, sy)){
                firePointer = pointer;
                fireHeld = true;
                return true;
            }

            if(insideBoostButton(sx, sy)){
                boostLatched = !boostLatched;
                return true;
            }

            return false;
        }

        private void handleDrag(int pointer, float sx, float sy){
            if(Core.settings.getBool(keyEditLayout, false)){
                touchDraggedEdit(pointer, sx, sy);
                return;
            }

            if(pointer == leftPointer){
                updateStick(left, sx, sy);
            }else if(pointer == rightPointer){
                updateStick(right, sx, sy);
            }else if(pointer == firePointer){
                fireHeld = insideFireButton(sx, sy);
            }
        }

        private void handleRelease(int pointer){
            if(pointer == leftPointer){
                leftPointer = -1;
                leftAmount = 0f;
                leftDir.setZero();
            }
            if(pointer == rightPointer){
                rightPointer = -1;
                rightAmount = 0f;
                rightDir.setZero();
            }
            if(pointer == firePointer){
                firePointer = -1;
                fireHeld = false;
            }
            if(pointer == dragPointer){
                dragPointer = -1;
                dragTarget = none;
                draggingEdit = false;
            }
        }

        private void updateDesktopPointer(){
            if(!mod.desktopDebugEnabled()){
                if(desktopMouseDown){
                    handleRelease(desktopPointer);
                    desktopMouseDown = false;
                }
                return;
            }

            float sx = Core.input.mouseX();
            float sy = Core.input.mouseY();
            boolean down = Core.input.keyDown(KeyCode.mouseLeft);

            if(down && !desktopMouseDown){
                if(handlePress(desktopPointer, sx, sy)){
                    desktopMouseDown = true;
                }
                return;
            }

            if(!desktopMouseDown) return;

            if(down){
                handleDrag(desktopPointer, sx, sy);
            }else{
                handleRelease(desktopPointer);
                desktopMouseDown = false;
            }
        }

        private int pickDragTarget(float sx, float sy){
            int best = none;
            float bestDst2 = Float.MAX_VALUE;

            int[] targets = showFireButton() ? new int[]{left, right, boost, fire} : new int[]{left, right, boost};
            for(int target : targets){
                if(target == right && !showRightStick()) continue;
                float cx = controlX(target);
                float cy = controlY(target);
                float r = controlSize(target) * 0.55f;
                float dst2 = Mathf.dst2(sx, sy, cx, cy);
                if(dst2 <= r * r && dst2 < bestDst2){
                    bestDst2 = dst2;
                    best = target;
                }
            }
            return best;
        }

        private void updateStick(int target, float sx, float sy){
            float cx = target == left ? centerX(keyLeftX, defLeftX) : centerX(keyRightX, defRightX);
            float cy = target == left ? centerY(keyLeftY, defLeftY) : centerY(keyRightY, defRightY);
            float size = target == left ? readSize(keyLeftSize, defLeftSize) : readSize(keyRightSize, defRightSize);

            float maxOffset = size * 0.30f;
            float dx = sx - cx;
            float dy = sy - cy;
            float len = Mathf.len(dx, dy);

            if(len <= 0.0001f || maxOffset <= 0.0001f){
                if(target == left){
                    leftDir.setZero();
                    leftAmount = 0f;
                }else{
                    rightDir.setZero();
                    rightAmount = 0f;
                }
                return;
            }

            float amount = Mathf.clamp(len / maxOffset);
            float nx = dx / len;
            float ny = dy / len;

            if(target == left){
                leftDir.set(nx, ny);
                leftAmount = amount;
            }else{
                rightDir.set(nx, ny);
                rightAmount = amount;
            }
        }

        private void drawStick(float cx, float cy, float size, Vec2 dir, float amount, float alpha, boolean isLeft, boolean editing){
            float baseHeight = size * (stickBase.height / Math.max(stickBase.width, 1f));
            float knobSize = size * 0.42f;
            float maxOffset = size * 0.30f;
            float ox = dir.x * amount * maxOffset;
            float oy = dir.y * amount * maxOffset;

            Draw.color(0.08f, 0.10f, 0.12f, alpha * 0.42f);
            Fill.circle(cx, cy, size * 0.43f);
            Draw.color(1f, 1f, 1f, alpha);
            Draw.rect(stickBase, cx, cy, size, baseHeight);

            Draw.color(0.12f, 0.14f, 0.18f, alpha * 0.55f);
            Fill.circle(cx + ox, cy + oy, knobSize * 0.33f);
            Draw.color(isLeft ? new Color(0.82f, 0.90f, 1f, 1f) : new Color(1f, 0.88f, 0.82f, 1f), alpha);
            Draw.rect(stickKnob, cx + ox, cy + oy, knobSize, knobSize);

            if(editing){
                Draw.color(isLeft ? Color.cyan : Color.orange, alpha);
                Lines.stroke(Scl.scl(2f));
                Lines.circle(cx, cy, size * 0.52f);
            }
        }

        private void drawButton(TextureRegion region, float cx, float cy, float size, float alpha, boolean active, boolean editing, Color tint){
            Draw.color(0.08f, 0.10f, 0.12f, alpha * 0.42f);
            Fill.circle(cx, cy, size * 0.54f);
            if(active){
                Draw.color(tint, alpha);
            }else{
                Draw.color(1f, 1f, 1f, alpha);
            }
            Draw.rect(region, cx, cy, size, size);

            if(editing){
                Draw.color(Color.yellow, alpha);
                Lines.stroke(Scl.scl(2f));
                Lines.circle(cx, cy, size * 0.52f);
            }
        }

        private boolean showRightStick(){
            return Core.settings.getBool(keyDualStickMode, true);
        }

        private boolean showFireButton(){
            return !Core.settings.getBool(keyDualStickMode, true);
        }

        private boolean insideLeftStick(float sx, float sy){
            return insideCircle(sx, sy, centerX(keyLeftX, defLeftX), centerY(keyLeftY, defLeftY), readSize(keyLeftSize, defLeftSize) * 0.55f);
        }

        private boolean insideRightStick(float sx, float sy){
            return insideCircle(sx, sy, centerX(keyRightX, defRightX), centerY(keyRightY, defRightY), readSize(keyRightSize, defRightSize) * 0.55f);
        }

        private boolean insideBoostButton(float sx, float sy){
            return insideCircle(sx, sy, centerX(keyBoostX, defBoostX), centerY(keyBoostY, defBoostY), readSize(keyBoostSize, defBoostSize) * 0.52f);
        }

        private boolean insideFireButton(float sx, float sy){
            return insideCircle(sx, sy, centerX(keyFireX, defFireX), centerY(keyFireY, defFireY), readSize(keyFireSize, defFireSize) * 0.52f);
        }

        private boolean insideCircle(float x, float y, float cx, float cy, float r){
            return Mathf.dst2(x, y, cx, cy) <= r * r;
        }

        private boolean insideControlArea(float x, float y){
            if(insideLeftStick(x, y)) return true;
            if(showRightStick() && insideRightStick(x, y)) return true;
            if(showFireButton() && insideFireButton(x, y)) return true;
            return insideBoostButton(x, y);
        }

        private boolean hasSelectedControl(){
            if(selectedTarget == none) return false;
            if(selectedTarget == right && !showRightStick()) return false;
            if(selectedTarget == fire && !showFireButton()) return false;
            return true;
        }

        private String selectedControlName(){
            switch(selectedTarget){
                case left: return Core.bundle.get("mjc.control.left");
                case right: return Core.bundle.get("mjc.control.right");
                case boost: return Core.bundle.get("mjc.control.boost");
                case fire: return Core.bundle.get("mjc.control.fire");
                default: return "";
            }
        }

        private int getSelectedScalePercent(){
            if(!hasSelectedControl()) return 100;
            return Math.round(controlSize(selectedTarget) * 100f / defaultSize(selectedTarget));
        }

        private void setSelectedScalePercent(int percent){
            if(!hasSelectedControl()) return;

            int clamped = Mathf.clamp(percent, 25, 400);
            int size = Math.round(defaultSize(selectedTarget) * clamped / 100f);

            switch(selectedTarget){
                case left:
                    Core.settings.put(keyLeftSize, size);
                    break;
                case right:
                    Core.settings.put(keyRightSize, size);
                    break;
                case boost:
                    Core.settings.put(keyBoostSize, size);
                    break;
                case fire:
                    Core.settings.put(keyFireSize, size);
                    break;
                default:
                    break;
            }
        }

        private int defaultSize(int target){
            switch(target){
                case left: return defLeftSize;
                case right: return defRightSize;
                case boost: return defBoostSize;
                case fire: return defFireSize;
                default: return 100;
            }
        }

        private float controlX(int target){
            switch(target){
                case left: return centerX(keyLeftX, defLeftX);
                case right: return centerX(keyRightX, defRightX);
                case boost: return centerX(keyBoostX, defBoostX);
                case fire: return centerX(keyFireX, defFireX);
                default: return 0f;
            }
        }

        private float controlY(int target){
            switch(target){
                case left: return centerY(keyLeftY, defLeftY);
                case right: return centerY(keyRightY, defRightY);
                case boost: return centerY(keyBoostY, defBoostY);
                case fire: return centerY(keyFireY, defFireY);
                default: return 0f;
            }
        }

        private float controlSize(int target){
            switch(target){
                case left: return readSize(keyLeftSize, defLeftSize);
                case right: return readSize(keyRightSize, defRightSize);
                case boost: return readSize(keyBoostSize, defBoostSize);
                case fire: return readSize(keyFireSize, defFireSize);
                default: return 0f;
            }
        }

        private float centerX(String key, int def){
            return getWidth() * readPercent(key, def);
        }

        private float centerY(String key, int def){
            return getHeight() * readPercent(key, def);
        }

        private float readPercent(String key, int def){
            int value = Mathf.clamp(Core.settings.getInt(key, def), 0, 100);
            return value / 100f;
        }

        private float readSize(String key, int def){
            return Mathf.clamp(Core.settings.getInt(key, def), 32, 320);
        }

        private TextureRegion findRegion(String shortName){
            TextureRegion error = Core.atlas.find("error");

            Mods.LoadedMod loaded = mods == null ? null : mods.getMod(MobileJoystickControlMod.class);
            if(loaded != null){
                TextureRegion current = Core.atlas.find(loaded.name + "-" + shortName);
                if(current != error) return current;
            }

            TextureRegion upper = Core.atlas.find("MobileJoystickControl-" + shortName);
            if(upper != error) return upper;

            TextureRegion lower = Core.atlas.find("mobilejoystickcontrol-" + shortName);
            if(lower != error) return lower;

            TextureRegion plain = Core.atlas.find(shortName);
            return plain != error ? plain : error;
        }
    }
}

