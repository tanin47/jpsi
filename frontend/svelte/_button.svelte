<script lang="ts">
export let isLoading = false

export let onClick: () => void
</script>
<button
  class="btn btn-outline btn-primary"
  class:btn-disabled={isLoading}
  onclick={onClick}
><slot/></button>
