window.SettingArea = {
  props: {
    isSetting: {
      type: Boolean,
      required: true
    }
  },
  emits: ['update:isSetting'],
  setup(props, {emit}) {
    const {ref, computed} = Vue;
    const specialKey = ref("")
    const normalKey = ref("")

    const closeClick = () => {
      emit('update:isSetting', false);
    }

    const saveClick = () => {
      if ((specialKey.value === "Ctrl" || specialKey.value === "Alt") && normalKey.value !== "") {
        window.javaBridge?.setHotkey(specialKey.value === "Ctrl" ? 2 : specialKey.value === "Alt" ? 1 : 0, normalKey.value.charCodeAt(0));
        emit('update:isSetting', false);
      }
    }

    const handleHotkey = (e) => {
      e.preventDefault()
      if ((e.ctrlKey || e.altKey) && ((e.keyCode >= 65 && e.keyCode <= 90) || (e.keyCode >= 48 && e.keyCode <= 57))) {
        specialKey.value = e.ctrlKey ? "Ctrl" : "Alt";
        normalKey.value = String.fromCharCode(e.keyCode);
      }
    }

    const inputKeys = computed(() => {
      if (specialKey.value && normalKey.value) {
        return `${specialKey.value}+${normalKey.value}`;
      } else {
        return ""
      }
    });

    window.addEventListener('javaReady', () => {
      const registeredKeys = window.javaBridge?.getCurrentHotKey();

      if (registeredKeys) {
        specialKey.value = registeredKeys.split("+")[0];
        normalKey.value = registeredKeys.split("+")[1];
      }

    }, {once: true});

    return {
      inputKeys,
      closeClick,
      saveClick,
      handleHotkey
    };
  },
  template: `
      <div
        class="settingsPanel"
        role="dialog"
        aria-modal="true"
        :class="{ open: isSetting }"
        >
        <div class="settingsHeader">
          <div class="settingsTitle">설정</div>
          <div class="closeX settingsClose" @click="closeClick">×</div>
        </div>
        <div class="settingsBody">
          <div class="settingsRow">
            <div class="settingsLabel">리셋 단축키</div>
            <input
              class="settingsInput"
              type="text"
              placeholder="여기를 클릭하고 단축키를 입력하세요"
              @keydown="handleHotkey"
              :value="inputKeys"
              />
            <div class="settingsHint">Ctrl/Alt 조합만 사용 가능합니다</div>
          </div>
        </div>
        <div class="settingsFooter">
          <button class="settingsBtnPrimary settingsSave" @click="saveClick">저장</button>
        </div>
      </div>
    `
};

