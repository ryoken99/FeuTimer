/*
 * Time formatting & BJJ preset catalogue matching pt.feubjj.timer.domain.presets.PresetCatalog
 */
(function () {
  'use strict';

  const root = (typeof window !== 'undefined' ? window : globalThis);
  root.FEU_TIMER = root.FEU_TIMER || {};

  function formatMmSs(totalSeconds) {
    const clamped = Math.max(0, Math.floor(totalSeconds));
    const minutes = Math.floor(clamped / 60);
    const seconds = clamped % 60;
    return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  function parseMmSs(label) {
    const parts = String(label).trim().split(':');
    if (parts.length !== 2) throw new Error(`Invalid MM:SS label: ${label}`);
    return Number(parts[0]) * 60 + Number(parts[1]);
  }

  function formatCompact(totalSeconds) {
    const clamped = Math.max(0, Math.floor(totalSeconds));
    if (clamped === 0) return '0s';
    const hours = Math.floor(clamped / 3600);
    const minutes = Math.floor((clamped % 3600) / 60);
    const seconds = clamped % 60;
    const out = [];
    if (hours > 0) out.push(`${hours}h`);
    if (minutes > 0) out.push(`${minutes}m`);
    if (seconds > 0) out.push(`${seconds}s`);
    return out.join(' ');
  }

  function displayValueAndUnit(totalSeconds) {
    const clamped = Math.max(0, Math.floor(totalSeconds));
    if (clamped >= 3600 && clamped % 3600 === 0) {
      return { value: clamped / 3600, unitKey: 'unit_hour' };
    }
    if (clamped >= 60 && clamped % 60 === 0) {
      return { value: clamped / 60, unitKey: 'unit_min' };
    }
    return { value: clamped, unitKey: 'unit_sec' };
  }

  function summaryBadge(config) {
    if (!config) return null;
    if (config.isStopwatch) return '00:00 ↑';
    if (config.repeat === -1) {
      return `∞ × ${formatCompact(config.workSeconds)} / ${formatCompact(config.restSeconds)}`;
    }
    if (!config.repeat || config.repeat <= 0) {
      return formatMmSs(config.workSeconds);
    }
    return `${config.repeat} × ${formatCompact(config.workSeconds)} / ${formatCompact(config.restSeconds)}`;
  }

  const QUICK_LABELS = [
    '00:30', '01:00', '02:00', '03:00', '04:00',
    '05:00', '06:00', '07:00', '08:00', '09:00',
    '10:00', '12:00', '15:00', '20:00', '30:00',
  ];

  const quickTimers = QUICK_LABELS.map((label) => ({
    label,
    seconds: parseMmSs(label),
  }));

  const roundPresets = [
    { id: 'adult_5x5', titleKey: 'preset_adult', repeat: 5, workSeconds: 300, restSeconds: 60 },
    { id: 'comp_6x6', titleKey: 'preset_comp', repeat: 6, workSeconds: 360, restSeconds: 30 },
    { id: 'drill_2x3', titleKey: 'preset_drill', repeat: 2, workSeconds: 180, restSeconds: 3 },
  ];

  const templateLibrary = [
    { id: 'tpl_open_5x1', titleKey: 'tpl_open_mat', repeat: -1, workSeconds: 300, restSeconds: 60 },
    { id: 'tpl_endurance_10x5', titleKey: 'tpl_endurance', repeat: 10, workSeconds: 300, restSeconds: 90 },
    { id: 'tpl_pro_4x10', titleKey: 'tpl_black_belt', repeat: 4, workSeconds: 600, restSeconds: 120 },
    { id: 'tpl_pace_10x3', titleKey: 'tpl_high_pace', repeat: 10, workSeconds: 180, restSeconds: 40 },
    { id: 'tpl_shark_4x1', titleKey: 'tpl_shark_tank', repeat: 4, workSeconds: 60, restSeconds: 60 },
    { id: 'tpl_tabata_8x20', titleKey: 'tpl_tabata', repeat: 8, workSeconds: 20, restSeconds: 10 },
  ];

  const ibjjfBelts = [
    { label: '2:00', beltKey: 'belt_kids_2m', seconds: 120 },
    { label: '3:00', beltKey: 'belt_kids_3m', seconds: 180 },
    { label: '4:00', beltKey: 'belt_kids_4m', seconds: 240 },
    { label: '5:00', beltKey: 'belt_white', seconds: 300 },
    { label: '6:00', beltKey: 'belt_blue', seconds: 360 },
    { label: '7:00', beltKey: 'belt_purple', seconds: 420 },
    { label: '8:00', beltKey: 'belt_brown', seconds: 480 },
    { label: '10:00', beltKey: 'belt_black', seconds: 600 },
  ];

  root.FEU_TIMER.TimeFormatter = {
    formatMmSs,
    parseMmSs,
    formatCompact,
    formatCompactDuration: formatCompact,
    displayValueAndUnit,
    summaryBadge,
  };

  root.FEU_TIMER.Presets = {
    quickTimers,
    roundPresets,
    templateLibrary,
    ibjjfBelts,
  };
})();
