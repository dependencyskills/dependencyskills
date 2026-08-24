// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	// Custom domain (verified at the org level), served at the apex — so no `base`
	// is needed and root-absolute asset paths are correct. `site` enables the
	// sitemap and canonical links. The CNAME in public/ ships in the build output.
	site: 'https://dependencyskills.org',
	integrations: [
		starlight({
			title: 'Dependency Skills',
			customCss: ['./src/styles/custom.css'],
			components: {
				Footer: './src/components/Footer.astro',
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/dependencyskills/dependencyskills' },
			],
			sidebar: [
				{ label: 'Overview', link: '/' },
				{ label: 'Adopted Standards', link: '/standards/' },
				{
					label: 'The case',
					items: [
						{ label: 'The findings', link: '/findings/' },
						{ label: 'What works, and what does not', link: '/what-works/' },
						{ label: 'What the tests show', link: '/experiments/' },
						{ label: 'The field as it stands', link: '/field/' },
						{ label: 'The landscape', link: '/landscape/' },
					],
				},
				{
					label: 'Security',
					items: [
						{ label: 'Prompt Injection', link: '/injection/' },
					],
				},
				{
					label: 'Case studies',
					items: [
						{ label: 'Thirteen slug functions', link: '/case-studies/thirteen-slug-functions/' },
						{ label: 'The datetime Instant that moved', link: '/case-studies/the-datetime-instant-move/' },
						{ label: 'The legacy library everyone remembers', link: '/case-studies/the-legacy-library-everyone-remembers/' },
						{ label: 'The dependency nobody declared', link: '/case-studies/the-dependency-nobody-declared/' },
					],
				},
				{
					label: 'Where it stands',
					items: [
						{ label: 'Decisions', link: '/decisions/' },
						{ label: 'The research', link: '/research/' },
					],
				},
				{
					label: 'How we work',
					items: [
						{ label: 'Methodology & tools', link: '/methodology/' },
					],
				},
			],
		}),
	],
});
