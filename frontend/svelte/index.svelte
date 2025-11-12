<script lang="ts">
import Button from './_button.svelte'


let isLoading = false
let javaMsg = ''

async function submit() {
  isLoading = true

  try {
    const resp = await (fetch('/ask-java', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({msg: 'Hello from JS'})
    }))

    const json = await resp.json()
    javaMsg = json.response
  } catch (e) {
    console.error(e)
  } finally {
    isLoading = false
  }
}

</script>

<div class="container mx-auto p-8 flex flex-col gap-6">
  <div class="text-2xl font-bold">Jpsi</div>
  <p>Build cross-platform desktop apps with Java, JavaScript, HTML, and CSS</p>
  <div>
    <Button {isLoading} onClick={submit}>
      Click to communicate with Java
    </Button>
  </div>
  {#if javaMsg}
    <div>Java said: {javaMsg}</div>
  {/if}
</div>

<style lang="scss">
</style>
