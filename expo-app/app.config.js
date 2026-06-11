const { withEntitlementsPlist } = require('@expo/config-plugins');

module.exports = ({ config }) => {
  return withEntitlementsPlist(config, (mod) => {
    delete mod.modResults['aps-environment'];
    return mod;
  });
};
