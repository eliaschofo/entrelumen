// Hide filler recipes from JEI without touching them in game (docs/design/recipe-design-rules.md).
// Twilight Forest 4.8.3345 shows Emperor's Cloth over every armour piece: its crafting recipe
// (twilightforest:emperors_cloth_recipe) and its template-free smithing recipe
// (twilightforest:emperors_cloth_smithing) head the uses of all armours. Both keep working on the
// crafting and smithing tables; only the viewers stop listing them.
// JEI has no data-driven recipe filter: KubeJS 2101.7.2 posts this client event from
// KubeJSJEIPlugin.onRuntimeAvailable and hides the matches with IRecipeManager.hideRecipes.
// EMI does not receive this client event; it reads kubejs/assets/emi/recipe/filters/ instead.
RecipeViewerEvents.removeRecipes(event => {
  event.remove(['twilightforest:emperors_cloth_recipe', 'twilightforest:emperors_cloth_smithing'])
})
